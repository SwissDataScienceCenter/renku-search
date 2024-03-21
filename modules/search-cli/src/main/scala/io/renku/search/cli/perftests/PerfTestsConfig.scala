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

package io.renku.search.cli.perftests

import cats.syntax.all.*
import com.monovore.decline.Opts
import org.http4s.Uri

final case class PerfTestsConfig(
    itemsToGenerate: Int,
    providers: List[Provider]
)

object PerfTestsConfig:

  private val itemsToGenerate: Opts[Int] =
    Opts
      .option[Int]("items-to-generate", "Number of items to generate. Default: 20")
      .withDefault(20)
      .validate("`items-to-generate` must be greater than 0")(_ > 0)

  val configOpts: Opts[PerfTestsConfig] =
    (itemsToGenerate, Provider.configOpts).mapN(PerfTestsConfig.apply)

sealed private trait Provider
private object Provider:
  val configOpts: Opts[List[Provider]] =
    (GitLab.configOpts.orNone, RandommerIO.configOpts.orNone)
      .mapN((gl, rnd) => List(gl, rnd).flatten)

  final case class GitLab(uri: Uri) extends Provider
  object GitLab:

    private val gitLab =
      Opts.flag("gitlab", "GitLab provider")

    private val uri: Opts[Uri] =
      Opts
        .option[String]("gitlab-url", "GitLab url")
        .validate("`gitlab-url` must be given")(_.trim.nonEmpty)
        .map(Uri.unsafeFromString)

    val configOpts: Opts[GitLab] =
      (gitLab, uri).mapN((_, url) => GitLab(url))

  final case class RandommerIO(apiKey: String) extends Provider
  object RandommerIO:

    private val randommerIO =
      Opts.flag("randommerio", "Randommer.io provider")

    private val apiKey: Opts[String] =
      Opts
        .option[String]("randommerIO-api-key", "User API key on randommer.io.")
        .validate("`randommerIO-api-key` must be given")(_.trim.nonEmpty)

    val configOpts: Opts[RandommerIO] =
      (randommerIO, apiKey).mapN((_, apiKey) => RandommerIO(apiKey))
