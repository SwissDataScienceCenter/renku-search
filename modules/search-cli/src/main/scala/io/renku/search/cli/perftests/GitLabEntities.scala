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

import java.time.Instant

import cats.syntax.all.*

import io.bullet.borer.Decoder
import io.bullet.borer.NullOptions.given
import io.bullet.borer.derivation.MapBasedCodecs.deriveDecoder
import io.bullet.borer.derivation.key
import io.renku.json.codecs.DateTimeDecoders
import io.renku.search.model.Visibility

final private case class GitLabProject(
    id: Int,
    description: Option[String] = None,
    name: String,
    path_with_namespace: String,
    http_url_to_repo: String,
    created_at: Instant,
    @key("tag_list") tagList: List[String],
    topics: List[String]
):
  val visibility: Visibility = Visibility.Public

  lazy val tagsAndTopics: List[String] =
    (tagList ::: topics).distinct

  lazy val namespace: String =
    path_with_namespace.lastIndexOf('/') match
      case n if n > 0 => path_with_namespace.drop(n)
      case _          => path_with_namespace

private object GitLabProject extends DateTimeDecoders:
  given Decoder[GitLabProject] = deriveDecoder

final private case class GitLabProjectUser(id: Int, name: String, username: String)

private object GitLabProjectUser:
  given Decoder[GitLabProjectUser] = deriveDecoder
