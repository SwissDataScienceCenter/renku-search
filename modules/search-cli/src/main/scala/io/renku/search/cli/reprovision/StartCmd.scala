package io.renku.search.cli.reprovision

import cats.effect.*
import cats.syntax.all.*

import com.monovore.decline.Opts
import io.renku.search.cli.{CommonOpts, Services}
import io.renku.search.events.ReprovisioningStarted
import io.renku.search.model.*

object StartCmd:

  final case class Options(id: Id):
    def asPayload: ReprovisioningStarted = ReprovisioningStarted(id)

  val opts: Opts[Options] =
    CommonOpts.idOpt.map(Options.apply)

  def apply(cfg: Options): IO[ExitCode] =
    Services.queueClient.use { queue =>
      for
        queuesCfg <- Services.queueConfig.load[IO]
        msg <- Services.createMessage(cfg.asPayload)
        _ <- queue.enqueue(queuesCfg.dataServiceAllEvents, msg)
      yield ExitCode.Success
    }
