package io.renku.search.provision

import cats.effect.*
import cats.effect.std.Semaphore
import cats.syntax.all.*
import fs2.{Pipe, Stream}

import io.renku.search.events.*
import io.renku.search.events.SyncEventMessage.syntax.*
import io.renku.search.model.Id
import io.renku.search.provision.SyncMessageHandler.Result
import io.renku.search.provision.handler.*
import io.renku.search.provision.handler.DeleteFromSolr.DeleteResult
import io.renku.search.provision.handler.PipelineSteps
import io.renku.search.provision.process.*
import io.renku.search.provision.reindex.ReprovisionService
import io.renku.search.sentry.{Level, Sentry, SentryEvent}
import io.renku.solr.client.UpsertResponse
import scribe.Scribe

final class SyncMessageHandler[F[_]: Async](
    ps: PipelineSteps[F],
    reprovisionService: ReprovisionService[F],
    control: SyncMessageHandler.Control[F],
    sentry: Sentry[F],
    maxConflictRetries: Int = 20
):

  private given logger: Scribe[F] = scribe.cats.effect[F]

  private val genericUpsert = GenericUpsert[F](ps)
  private val genericDelete = GenericDelete[F](ps)
  private val projectUpsert = ProjectUpsert[F](ps)
  private val userDelete = UserDelete[F](ps)
  private val groupUpdate = GroupUpdate[F](ps)
  private val groupRemove = GroupRemove[F](ps)
  private val groupMemberUpsert = GroupMemberUpsert[F](ps)
  private val reprovisioning = Reprovisioning[F](reprovisionService)

  def create: Stream[F, Result] = ps.reader.readSyncEvents.through(processEvents)

  def processEvents: Pipe[F, SyncEventMessage, Result] =
    _.evalMap { msg =>
      for
        _ <- control.await
        _ <- MessageMetrics.countReceived(msg)
        r <- processEvent(msg)
        _ <- MessageMetrics.countResult(msg, r)
      yield r
    }

  def processEvent(m: SyncEventMessage): F[Result] =
    m.header.msgType match
      case mt: MsgType.ProjectCreated.type =>
        markMessage(m)(
          projectUpsert.process(mt.cast(m), maxConflictRetries).map(Result.Upsert.apply)
        )

      case mt: MsgType.ProjectUpdated.type =>
        markMessage(m)(
          projectUpsert.process(mt.cast(m), maxConflictRetries).map(Result.Upsert.apply)
        )

      case mt: MsgType.ProjectRemoved.type =>
        markMessage(m)(genericDelete.process(mt.cast(m)).map(Result.Delete.apply))
          .flatTap(res =>
            SentryEvent
              .create[F](Level.Debug, s"$mt => $res")
              .flatMap(sentry.capture)
          )

      case mt: MsgType.ProjectMemberAdded.type =>
        markMessage(m)(
          genericUpsert.process(mt.cast(m), maxConflictRetries).map(Result.Upsert.apply)
        )

      case mt: MsgType.ProjectMemberUpdated.type =>
        markMessage(m)(
          genericUpsert.process(mt.cast(m), maxConflictRetries).map(Result.Upsert.apply)
        )

      case mt: MsgType.ProjectMemberRemoved.type =>
        markMessage(m)(
          genericUpsert.process(mt.cast(m), maxConflictRetries).map(Result.Upsert.apply)
        )

      case mt: MsgType.UserAdded.type =>
        markMessage(m)(
          genericUpsert.process(mt.cast(m), maxConflictRetries).map(Result.Upsert.apply)
        )

      case mt: MsgType.UserUpdated.type =>
        markMessage(m)(
          genericUpsert.process(mt.cast(m), maxConflictRetries).map(Result.Upsert.apply)
        )

      case mt: MsgType.UserRemoved.type =>
        val msg = mt.cast(m)
        markMessage(m)(userDelete.process(msg).map(Result.Delete.apply))
          .flatTap(res =>
            SentryEvent
              .create[F](Level.Debug, s"$mt => $res")
              .flatMap(sentry.capture)
          )

      case mt: MsgType.GroupAdded.type =>
        markMessage(m)(
          genericUpsert.process(mt.cast(m), maxConflictRetries).map(Result.Upsert.apply)
        )

      case mt: MsgType.GroupUpdated.type =>
        markMessage(m)(
          groupUpdate.process(mt.cast(m), maxConflictRetries).map(Result.Upsert.apply)
        )

      case mt: MsgType.GroupRemoved.type =>
        markMessage(m)(
          groupRemove.process(mt.cast(m), maxConflictRetries).map(Result.Delete.apply)
        )
          .flatTap(res =>
            SentryEvent
              .create[F](Level.Debug, s"$mt => $res")
              .flatMap(sentry.capture)
          )

      case mt: MsgType.GroupMemberAdded.type =>
        markMessage(m)(
          groupMemberUpsert
            .process(mt.cast(m), maxConflictRetries)
            .map(Result.Upsert.apply)
        )

      case mt: MsgType.GroupMemberUpdated.type =>
        markMessage(m)(
          groupMemberUpsert
            .process(mt.cast(m), maxConflictRetries)
            .map(Result.Upsert.apply)
        )

      case mt: MsgType.GroupMemberRemoved.type =>
        markMessage(m)(
          groupMemberUpsert
            .process(mt.cast(m), maxConflictRetries)
            .map(Result.Upsert.apply)
        )

      case mt: MsgType.ReprovisioningStarted.type =>
        // currently there are more than one stream handler pausing is
        // only for this one, so it is necessary to do the "full"
        // re-index, which restarts all handlers. For this it must be
        // run on a separate thread. Ideally, when only this handler
        // is active, the pause is sufficient and we can use
        // `resetIndex` instead of `startReIndex`
        control.whilePaused.use { _ =>
          for
            _ <- SentryEvent
              .create[F](Level.Debug, s"Received $mt message")
              .flatMap(sentry.capture)
            res <- Deferred[F, Option[Id]]
            _ <- reprovisioning.processStart(
              mt.cast(m),
              id => res.complete(id).void
            )
            id <- res.get
          yield Result.ReprovisionStart(id)
        }

      case mt: MsgType.ReprovisioningFinished.type =>
        markMessage(m)(
          reprovisioning
            .processFinish(mt.cast(m))
            .map(Result.ReprovisionFinish.apply)
        )

  private def markMessage[A](m: SyncEventMessage)(fa: F[A]): F[A] =
    Resource
      .onFinalizeCase[F] {
        case Resource.ExitCase.Succeeded   => ps.reader.markProcessed(m.id)
        case Resource.ExitCase.Canceled    => ps.reader.markProcessed(m.id)
        case Resource.ExitCase.Errored(ex) => ps.reader.markProcessedError(ex, m.id)
      }
      .use(_ => fa)

object SyncMessageHandler:
  def apply[F[_]: Async](
      ps: PipelineSteps[F],
      reprovisionService: ReprovisionService[F],
      sentry: Sentry[F],
      maxConflictRetries: Int = 20
  ): F[SyncMessageHandler[F]] =
    Control[F].map(c =>
      new SyncMessageHandler[F](ps, reprovisionService, c, sentry, maxConflictRetries)
    )

  final class Control[F[_]: Sync](s: Semaphore[F]) {
    def await: F[Unit] = s.acquire >> s.release
    def pause: F[Unit] = s.acquire
    def resume: F[Unit] = s.release
    def whilePaused: Resource[F, Unit] =
      Resource.make(pause)(_ => resume)
  }
  object Control {
    def apply[F[_]: Async] = Semaphore[F](1).map(new Control(_))
  }

  enum Result:
    case Upsert(value: UpsertResponse)
    case Delete(value: DeleteFromSolr.DeleteResult[?])
    case ReprovisionStart(id: Option[Id])
    case ReprovisionFinish(id: Option[Id])

    def fold[A](
        fu: UpsertResponse => A,
        fd: DeleteFromSolr.DeleteResult[?] => A,
        frs: ReprovisionStart => A,
        frf: ReprovisionFinish => A
    ): A =
      this match
        case Upsert(r)            => fu(r)
        case Delete(r)            => fd(r)
        case r: ReprovisionStart  => frs(r)
        case r: ReprovisionFinish => frf(r)

    def asUpsert: Option[UpsertResponse] = fold(Some(_), _ => None, _ => None, _ => None)
