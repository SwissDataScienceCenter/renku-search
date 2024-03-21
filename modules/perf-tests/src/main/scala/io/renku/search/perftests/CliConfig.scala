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

package io.renku.search.perftests

import cats.syntax.all.*
import com.monovore.decline.Opts

final private case class CliConfig(itemsToGenerate: Int, randommerIoApiKey: String)

private object CliConfig:

  private val itemsToGenerate: Opts[Int] =
    Opts
      .option[Int]("items-to-generate", "Number of items to generate. Default: 20")
      .withDefault(20)
      .validate("`items-to-generate` must be greater than 0")(_ > 0)

  private val randommerIoApiKey: Opts[String] =
    Opts
      .option[String]("randommerIO-api-key", "User API key on randommer.io.")
      .validate("`randommerIO-api-key` must be given")(_.trim.nonEmpty)

  def configOpts: Opts[CliConfig] =
    (itemsToGenerate, randommerIoApiKey).mapN(CliConfig.apply)
