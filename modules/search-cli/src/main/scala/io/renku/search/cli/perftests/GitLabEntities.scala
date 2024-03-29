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
import io.bullet.borer.Decoder
import io.bullet.borer.NullOptions.given
import io.bullet.borer.derivation.MapBasedCodecs.deriveDecoder
import io.renku.search.borer.codecs.DateTimeDecoders
import io.renku.search.model.projects

import java.time.Instant

final private case class GitLabProject(
    id: Int,
    description: Option[String] = None,
    name: String,
    path_with_namespace: String,
    http_url_to_repo: String,
    created_at: Instant
):
  val visibility: projects.Visibility = projects.Visibility.Public

private object GitLabProject extends DateTimeDecoders:
  given Decoder[GitLabProject] = deriveDecoder

final private case class GitLabProjectUser(id: Int, name: String)

private object GitLabProjectUser:
  given Decoder[GitLabProjectUser] = deriveDecoder
