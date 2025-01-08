package io.renku.search.cli.users

import cats.effect.*
import cats.syntax.all.*

import com.monovore.decline.Opts
import io.renku.search.cli.{CommonOpts, Services}
import io.renku.search.events.UserUpdated
import io.renku.search.model.*

object UpdateCmd:

  final case class Options(
      id: Id,
      ns: Namespace,
      first: Option[FirstName],
      last: Option[LastName],
      email: Option[Email]
  ):
    def asPayload: UserUpdated = UserUpdated(id, ns, first, last, email)

  val opts: Opts[Options] =
    (
      CommonOpts.idOpt,
      CommonOpts.namespaceOpt,
      CommonOpts.firstName,
      CommonOpts.lastName,
      CommonOpts.email
    ).mapN(Options.apply)

  def apply(cfg: Options): IO[ExitCode] =
    Services.queueClient.use { queue =>
      for
        queuesCfg <- Services.queueConfig.load[IO]
        msg <- Services.createMessage(cfg.asPayload)
        _ <- queue.enqueue(queuesCfg.dataServiceAllEvents, msg)
      yield ExitCode.Success
    }
