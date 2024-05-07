/*
 * Copyright 2024 Swiss Data Science Center (SDSC)
 * A partnership between École Polytechnique Fédérale de Lausanne (EPFL) and
 * Eidgenössische Technische Hochschule Zürich (ETHZ).
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.renku.search.cli.projects

import cats.effect.*
import cats.syntax.all.*
import com.monovore.decline.Opts
import io.renku.search.config.QueuesConfig
import io.renku.search.cli.{CommonOpts, Services}
import io.renku.search.model.*
import io.renku.search.events.ProjectUpdated

object UpdateCmd extends CommonOpts:

  final case class Options(
      id: Id,
      name: Name,
      namespace: Namespace,
      slug: projects.Slug,
      visibility: projects.Visibility,
      repositories: Seq[projects.Repository],
      description: Option[projects.Description],
      keywords: Seq[Keyword]
  ):
    def asPayload: ProjectUpdated = ProjectUpdated(
      id,
      name,
      namespace,
      slug,
      visibility,
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
      repositories,
      projectDescription,
      keywords
    ).mapN(Options.apply)

  def apply(cfg: Options): IO[ExitCode] =
    Services.queueClient.use { queue =>
      for
        queuesCfg <- QueuesConfig.config.load[IO]
        msg <- Services.createMessage(cfg.asPayload)
        _ <- queue.enqueue(queuesCfg.projectUpdated, msg)
      yield ExitCode.Success
    }
