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

import io.bullet.borer.*
import io.bullet.borer.derivation.MapBasedCodecs
import io.renku.search.model.Id
import io.renku.search.model.projects.*
import io.renku.search.solr.documents.Project as ProjectDocument
import io.renku.search.solr.schema.EntityDocumentSchema.Fields as SolrField
import io.renku.solr.client.EncoderSupport

sealed trait PartialEntityDocument extends SolrDocument:
  def applyTo(e: EntityDocument): EntityDocument

object PartialEntityDocument:
  given AdtEncodingStrategy =
    AdtEncodingStrategy.flat(typeMemberName = SolrField.entityType.name)

  given Encoder[PartialEntityDocument] = EncoderSupport.derive[PartialEntityDocument]
  given Decoder[PartialEntityDocument] =
    MapBasedCodecs.deriveAllDecoders[PartialEntityDocument]
  given Codec[PartialEntityDocument] = Codec.of[PartialEntityDocument]

  // we must name it the same as the "full" entity documents so that borer
  // generates the same discriminator (it is not configurable)
  final case class Project(
      id: Id,
      owners: Set[Id] = Set.empty,
      members: Set[Id] = Set.empty
  ) extends PartialEntityDocument:
    def remove(id: Id): Project = copy(owners = owners - id, members = members - id)
    def add(id: Id, role: MemberRole): Project =
      role match
        case MemberRole.Owner  => copy(owners = owners + id, members = members - id)
        case MemberRole.Member => copy(members = members + id, owners = owners - id)

    def applyTo(e: EntityDocument): EntityDocument =
      e match
        case p: ProjectDocument if p.id == id =>
          p.addMembers(MemberRole.Owner, owners.toList)
            .addMembers(MemberRole.Member, members.toList)
        case _ => e

    def combine(p: Project): Project =
      if (p.id == id)
        p.copy(
          members = p.members ++ (members -- p.owners),
          owners = p.owners ++ (owners -- p.members)
        )
      else p

    def applyTo(e: PartialEntityDocument): PartialEntityDocument =
      e match
        case p: Project => combine(p)

  object Project:
    given Encoder[Project] =
      EncoderSupport.deriveWith(
        DocumentKind.PartialEntity.additionalField,
        EncoderSupport.AdditionalFields.productPrefix(SolrField.entityType.name)
      )
