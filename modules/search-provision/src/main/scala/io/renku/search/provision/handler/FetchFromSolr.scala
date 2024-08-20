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

package io.renku.search.provision.handler

import cats.effect.Sync
import cats.syntax.all.*
import fs2.Stream

import io.bullet.borer.Decoder
import io.bullet.borer.derivation.MapBasedCodecs
import io.renku.search.events.EventMessage
import io.renku.search.model.{EntityType, Id, Namespace}
import io.renku.search.provision.handler.FetchFromSolr.*
import io.renku.search.solr.client.SearchSolrClient
import io.renku.search.solr.documents.DocumentKind
import io.renku.search.solr.documents.{
  Group as GroupDocument,
  Project as ProjectDocument,
  *
}
import io.renku.search.solr.query.SolrToken
import io.renku.search.solr.schema.EntityDocumentSchema
import io.renku.solr.client.{QueryData, QueryString}

trait FetchFromSolr[F[_]]:
  def fetchEntityForUser(userId: Id): Stream[F, EntityId]
  def loadProjectsByGroup[A](msg: EntityOrPartialMessage[A]): F[EntityOrPartialMessage[A]]
  def loadEntityOrPartial[A](using IdExtractor[A])(
      msg: EventMessage[A]
  ): F[EntityOrPartialMessage[A]]
  def loadProjectGroups(
      msg: EventMessage[EntityOrPartial]
  ): F[EntityOrPartialMessage[EntityOrPartial]]
  def fetchEntityOrPartialById[A](v: A)(using IdExtractor[A]): F[Option[EntityOrPartial]]

object FetchFromSolr:
  final case class EntityId(id: Id)
  object EntityId:
    given Decoder[EntityId] = MapBasedCodecs.deriveDecoder
    given IdExtractor[EntityId] = _.id

  def apply[F[_]: Sync](
      solrClient: SearchSolrClient[F]
  ): FetchFromSolr[F] =
    new FetchFromSolr[F] {
      val logger = scribe.cats.effect[F]

      def loadProjectGroups(
          msg: EventMessage[EntityOrPartial]
      ): F[EntityOrPartialMessage[EntityOrPartial]] =
        val namespaces =
          msg.payload.flatMap {
            case p: ProjectDocument               => p.namespace
            case p: PartialEntityDocument.Project => p.namespace
            case _                                => None
          }
        val query = List(
          SolrToken.entityTypeIs(EntityType.Group),
          SolrToken.kindIs(DocumentKind.FullEntity),
          namespaces.map(SolrToken.namespaceIs).foldOr
        ).foldAnd

        solrClient
          .queryAll[EntityDocument](QueryData(QueryString(query.value)))
          .compile
          .toList
          .map(groups =>
            EntityOrPartialMessage(msg, (msg.payload ++ groups).map(e => e.id -> e).toMap)
          )

      def loadProjectsByGroup[A](
          msg: EntityOrPartialMessage[A]
      ): F[EntityOrPartialMessage[A]] =
        val namespaces = msg.documents.values.collect { case g: GroupDocument =>
          g.namespace
        }.toSeq

        val query = List(
          SolrToken.entityTypeIs(EntityType.Project),
          SolrToken.kindIs(DocumentKind.FullEntity),
          namespaces.map(SolrToken.namespaceIs).foldOr
        ).foldAnd
        solrClient
          .queryAll[EntityDocument](QueryData(QueryString(query.value)))
          .compile
          .toList
          .map(msg.appendDocuments)

      def fetchEntityForUser(userId: Id): Stream[F, EntityId] =
        val query = QueryString(SolrToken.anyMemberIs(userId).value)
        solrClient.queryAll[EntityId](
          QueryData(query).withFields(EntityDocumentSchema.Fields.id)
        )

      def loadEntityOrPartial[A](using IdExtractor[A])(
          msg: EventMessage[A]
      ): F[EntityOrPartialMessage[A]] =
        val loaded =
          msg.payload
            .traverse(fetchEntityOrPartialById)
            .map(_.flatten.map(e => e.id -> e).toMap)
        loaded.map(docs => EntityOrPartialMessage(msg, docs))

      def fetchEntityOrPartialById[A](v: A)(using
          IdExtractor[A]
      ): F[Option[EntityOrPartial]] =
        val id = IdExtractor[A].getId(v)
        val eid = CompoundId.entity(id)
        val pid = CompoundId.partial(id)
        solrClient.findById[EntityDocument](eid).flatMap {
          case Some(e) => (e: EntityOrPartial).some.pure[F]
          case None =>
            logger.debug(
              s"Did not find entity document for id $id, look for a partial"
            ) >>
              solrClient
                .findById[PartialEntityDocument](pid)
                .map(_.map(e => e: EntityOrPartial))
        }
    }
