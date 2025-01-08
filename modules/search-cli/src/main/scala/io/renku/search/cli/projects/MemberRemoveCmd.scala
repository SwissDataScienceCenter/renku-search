package io.renku.search.cli.projects

import cats.effect.*
import cats.syntax.all.*

import com.monovore.decline.Opts
import io.renku.search.cli.{CommonOpts, Services}
import io.renku.search.events.ProjectMemberRemoved
import io.renku.search.model.*

object MemberRemoveCmd:

  final case class Options(project: Id, user: Id):
    def asPayload: ProjectMemberRemoved = ProjectMemberRemoved(project, user)

  val opts: Opts[Options] =
    (CommonOpts.projectIdOpt, CommonOpts.userIdOpt).mapN(Options.apply)

  def apply(cfg: Options): IO[ExitCode] =
    Services.queueClient.use { queue =>
      for
        queuesCfg <- Services.queueConfig.load[IO]
        msg <- Services.createMessage(cfg.asPayload)
        _ <- queue.enqueue(queuesCfg.dataServiceAllEvents, msg)
      yield ExitCode.Success
    }
