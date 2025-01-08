package io.renku.solr.client

import scala.concurrent.duration.*

import cats.effect.*
import cats.effect.std.Random
import cats.syntax.all.*

enum UpsertResponse:
  case Success(header: ResponseHeader)
  case VersionConflict

  lazy val isSuccess: Boolean = this match
    case Success(_)      => true
    case VersionConflict => false

  lazy val isFailure: Boolean = !isSuccess

object UpsertResponse:

  def retryOnConflict[F[_]: Async](
      retries: Int,
      maxWait: Duration = 100.millis
  )(fa: F[UpsertResponse]): F[UpsertResponse] =
    val logger = scribe.cats.effect[F]
    fa.flatMap {
      case r @ UpsertResponse.Success(_) => r.pure[F]
      case r @ UpsertResponse.VersionConflict =>
        if (retries <= 0) logger.warn("Retries on version conflict exceeded").as(r)
        else
          for
            _ <- logger.info(
              s"Version conflict on SOLR update, retries left $retries"
            )
            rand <- Random.scalaUtilRandom[F]
            waitMillis <- rand.betweenLong(5, math.max(maxWait.toMillis, 10))
            _ <- logger.debug(s"Wait ${waitMillis}ms before next try")
            _ <- Async[F].sleep(waitMillis.millis)
            res <- retryOnConflict(retries - 1, maxWait)(fa)
          yield res
    }

  object syntax {
    extension [F[_]](self: F[UpsertResponse])
      def retryOnConflict(
          retries: Int,
          maxWait: Duration = 100.millis
      )(using Async[F]): F[UpsertResponse] =
        UpsertResponse.retryOnConflict(retries, maxWait)(self)
  }
