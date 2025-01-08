package io.renku.search.cli.groups

import cats.effect.*
import cats.syntax.all.*

import com.monovore.decline.Opts
import io.renku.search.cli.{CommonOpts, Services}
import io.renku.search.events.GroupUpdated
import io.renku.search.model.*

object UpdateCmd:

  final case class Options(id: Id, name: Name, ns: Namespace):
    def asPayload: GroupUpdated = GroupUpdated(id, name, ns, None)

  val opts: Opts[Options] =
    (CommonOpts.idOpt, CommonOpts.nameOpt, CommonOpts.namespaceOpt).mapN(Options.apply)

  def apply(cfg: Options): IO[ExitCode] =
    Services.queueClient.use { queue =>
      for
        queuesCfg <- Services.queueConfig.load[IO]
        msg <- Services.createMessage(cfg.asPayload)
        _ <- queue.enqueue(queuesCfg.dataServiceAllEvents, msg)
      yield ExitCode.Success
    }
