package io.renku.search.cli.projects

import cats.effect.*
import cats.syntax.all.*

import com.monovore.decline.Opts
import io.renku.search.cli.{CommonOpts, Services}
import io.renku.search.events.ProjectCreated
import io.renku.search.model.*

object CreateCmd extends CommonOpts:

  final case class Options(
      id: Id,
      name: Name,
      namespace: Namespace,
      slug: Slug,
      visibility: Visibility,
      createdBy: Id,
      creationDate: Timestamp,
      repositories: Seq[Repository],
      description: Option[Description],
      keywords: Seq[Keyword]
  ):
    def asPayload: ProjectCreated = ProjectCreated(
      id,
      name,
      namespace,
      slug,
      visibility,
      createdBy,
      creationDate,
      repositories,
      description,
      keywords
    )

  val opts: Opts[Options] =
    (
      idOpt,
      nameOpt,
      namespaceOpt,
      projectSlug,
      projectVisibility,
      Opts.option[Id]("created-by", "The user id of the user creating the project"),
      currentTime,
      repositories,
      projectDescription,
      keywords
    ).mapN(Options.apply)

  def apply(cfg: Options): IO[ExitCode] =
    Services.queueClient.use { queue =>
      for
        queuesCfg <- Services.queueConfig.load[IO]
        msg <- Services.createMessage(cfg.asPayload)
        _ <- queue.enqueue(queuesCfg.dataServiceAllEvents, msg)
      yield ExitCode.Success
    }
