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
import io.bullet.borer.NullOptions.given
import io.bullet.borer.derivation.{MapBasedCodecs, key}
import io.renku.search.model.*
import io.renku.search.solr.documents.{Group as GroupDocument, Project as ProjectDocument}
import io.renku.search.solr.schema.EntityDocumentSchema.Fields as SolrField
import io.renku.solr.client.{DocVersion, EncoderSupport}

sealed trait PartialEntityDocument extends SolrDocument:
  def applyTo(e: EntityDocument): EntityDocument
  def setVersion(v: DocVersion): PartialEntityDocument

object PartialEntityDocument:
  given AdtEncodingStrategy =
    AdtEncodingStrategy.flat(typeMemberName = SolrField.entityType.name)

  given Encoder[PartialEntityDocument] = EncoderSupport.derive[PartialEntityDocument]
  given Decoder[PartialEntityDocument] =
    MapBasedCodecs.deriveAllDecoders[PartialEntityDocument]
  given Codec[PartialEntityDocument] = Codec.of[PartialEntityDocument]

  // we must name it the same as the "full" entity documents so that borer
  // *generates and decodes* the same discriminator (it is not configurable)
  final case class Project(
      id: Id,
      @key("_version_") version: DocVersion = DocVersion.Off,
      name: Option[Name] = None,
      namespace: Option[Namespace] = None,
      slug: Option[Slug] = None,
      repositories: Seq[Repository] = Seq.empty,
      visibility: Option[Visibility] = None,
      description: Option[Description] = None,
      keywords: List[Keyword] = Nil,
      owners: Set[Id] = Set.empty,
      editors: Set[Id] = Set.empty,
      viewers: Set[Id] = Set.empty,
      groupOwners: Set[Id] = Set.empty,
      groupEditors: Set[Id] = Set.empty,
      groupViewers: Set[Id] = Set.empty,
      members: Set[Id] = Set.empty
  ) extends PartialEntityDocument:
    def setVersion(v: DocVersion): Project = copy(version = v)

    def toEntityMembers: EntityMembers =
      EntityMembers(owners, editors, viewers, members)

    def toGroupMembers: EntityMembers =
      EntityMembers(groupOwners, groupEditors, groupViewers, Set.empty)

    def setMembers(em: EntityMembers): Project =
      copy(
        owners = em.owners,
        editors = em.editors,
        viewers = em.viewers,
        members = em.members
      )

    def setGroupMembers(em: EntityMembers): Project =
      copy(groupOwners = em.owners, groupEditors = em.editors, groupViewers = em.viewers)

    def modifyEntityMembers(f: EntityMembers => EntityMembers): Project =
      setMembers(f(toEntityMembers))

    def modifyGroupMembers(f: EntityMembers => EntityMembers): Project =
      setGroupMembers(f(toGroupMembers))

    def applyTo(e: EntityDocument): EntityDocument =
      e match
        case p: ProjectDocument if p.id == id =>
          p.modifyEntityMembers(_ ++ toEntityMembers)
            .copy(
              name = name.getOrElse(p.name),
              namespace = namespace.orElse(p.namespace),
              slug = slug.getOrElse(p.slug),
              repositories = if (repositories.isEmpty) p.repositories else repositories,
              visibility = visibility.getOrElse(p.visibility),
              description = description.orElse(p.description),
              keywords = Option.when(keywords.nonEmpty)(keywords).getOrElse(p.keywords)
            )
            .setVersion(version)
        case _ => e

  object Project:
    given Encoder[Project] =
      EncoderSupport.deriveWith(
        DocumentKind.PartialEntity.additionalField,
        EncoderSupport.AdditionalFields.productPrefix(SolrField.entityType.name)
      )

  final case class Group(
      id: Id,
      @key("_version_") version: DocVersion = DocVersion.Off,
      name: Option[Name] = None,
      namespace: Option[Namespace] = None,
      description: Option[Description] = None,
      owners: Set[Id] = Set.empty,
      editors: Set[Id] = Set.empty,
      viewers: Set[Id] = Set.empty
  ) extends PartialEntityDocument:
    def setVersion(v: DocVersion): Group = copy(version = v)

    private def toEntityMembers: EntityMembers =
      EntityMembers(owners, editors, viewers, Set.empty)

    def setMembers(em: EntityMembers): Group =
      copy(
        owners = em.owners,
        editors = em.editors,
        viewers = em.viewers
      )

    def modifyEntityMembers(f: EntityMembers => EntityMembers): Group =
      setMembers(f(toEntityMembers))

    def applyTo(e: EntityDocument): EntityDocument =
      e match
        case g: GroupDocument if g.id == id =>
          g.copy(
            name = name.getOrElse(g.name),
            namespace = namespace.getOrElse(g.namespace),
            description = description.orElse(g.description)
          ).setVersion(version)
        case _ => e

  object Group:
    given Encoder[Group] =
      EncoderSupport.deriveWith(
        DocumentKind.PartialEntity.additionalField,
        EncoderSupport.AdditionalFields.productPrefix(SolrField.entityType.name)
      )
