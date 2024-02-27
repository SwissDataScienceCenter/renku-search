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

package io.renku.search.solr.documents

import io.bullet.borer.NullOptions.given
import io.bullet.borer.derivation.MapBasedCodecs.deriveDecoder
import io.bullet.borer.{Decoder, Encoder}
import io.renku.search.model.*
import io.renku.solr.client.EncoderSupport.deriveWithDiscriminator

final case class Project(
    id: projects.Id,
    name: projects.Name,
    slug: projects.Slug,
    repositories: Seq[projects.Repository] = Seq.empty,
    visibility: projects.Visibility,
    description: Option[projects.Description] = None,
    createdBy: users.Id,
    creationDate: projects.CreationDate,
    members: Seq[users.Id] = Seq.empty,
    score: Option[Double] = None
)

object Project:
  val entityType: String = "Project"

  given Encoder[Project] = deriveWithDiscriminator
  given Decoder[Seq[User]] =
    Decoder[Seq[User]] { reader =>
      if reader.hasArrayStart then Decoder.forArray[User].map(_.toSeq).read(reader)
      else Decoder[User].map(Seq(_)).read(reader)
    }
  given Decoder[Project] = deriveDecoder
