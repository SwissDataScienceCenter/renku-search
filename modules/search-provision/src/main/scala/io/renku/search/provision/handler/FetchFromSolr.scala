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
import fs2.{Pipe, Stream}
import io.bullet.borer.Decoder
import io.bullet.borer.derivation.MapBasedCodecs
import io.renku.search.events.EventMessage
import io.renku.search.model.{EntityType, Id, Namespace}
import io.renku.search.provision.handler.FetchFromSolr.*
import io.renku.search.solr.client.SearchSolrClient
import io.renku.search.solr.documents.{
  CompoundId,
  EntityDocument,
  Group => GroupDocument,
  PartialEntityDocument
}
import io.renku.search.solr.query.SolrToken
import io.renku.solr.client.{QueryData, QueryString}
import io.renku.search.solr.documents.DocumentKind

trait FetchFromSolr[F[_]]:
  def fetchById[A: Decoder](id: CompoundId): Stream[F, A]
  def fetchProjectForUser(userId: Id): Stream[F, FetchFromSolr.ProjectId]
  def fetchProjectByNamespace(ns: Namespace): Stream[F, FetchFromSolr.ProjectId]
  def fetchProjectsByGroup[A]
      : Pipe[F, EntityOrPartialMessage[A], EntityOrPartialMessage[A]]
  def fetchEntityOrPartial[A](using
      IdExtractor[A]
  ): Pipe[F, EventMessage[A], EntityOrPartialMessage[A]]

  def fetchEntityOrPartialById[A](v: A)(using IdExtractor[A]): F[Option[EntityOrPartial]]

object FetchFromSolr:
  final case class ProjectId(id: Id)
  object ProjectId:
    given Decoder[ProjectId] = MapBasedCodecs.deriveDecoder
    given IdExtractor[ProjectId] = _.id

  def apply[F[_]: Sync](
      solrClient: SearchSolrClient[F]
  ): FetchFromSolr[F] =
    new FetchFromSolr[F] {
      val logger = scribe.cats.effect[F]

      def fetchProjectsByGroup[A]
          : Pipe[F, EntityOrPartialMessage[A], EntityOrPartialMessage[A]] =
        _.evalMap { msg =>
          val namespaces = msg.documents.values.collect { case g: GroupDocument =>
            g.namespace
          }.toSeq

          val query = List(
            SolrToken.entityTypeIs(EntityType.Project),
            SolrToken.kindIs(DocumentKind.FullEntity),
            namespaces.map(SolrToken.namespaceIs).foldOr
          ).foldAnd
          solrClient.queryAll[EntityDocument](QueryData(QueryString(query.value)))
            .compile.toList
            .map(msg.withDocuments)
        }

      def fetchById[A: Decoder](id: CompoundId): Stream[F, A] =
        Stream.eval(solrClient.findById(id)).unNone

      def fetchProjectForUser(userId: Id): Stream[F, FetchFromSolr.ProjectId] =
        val query = QueryString(
          List(
            SolrToken.entityTypeIs(EntityType.Project),
            SolrToken.anyMemberIs(userId)
          ).foldAnd.value
        )
        solrClient.queryAll[ProjectId](QueryData(query))

      def fetchProjectByNamespace(ns: Namespace): Stream[F, FetchFromSolr.ProjectId] =
        val query = QueryString(
          List(
            SolrToken.entityTypeIs(EntityType.Project),
            SolrToken.namespaceIs(ns)
          ).foldAnd.value
        )
        solrClient.queryAll[ProjectId](QueryData(query))

      def fetchEntityOrPartial[A](using
          IdExtractor[A]
      ): Pipe[F, EventMessage[A], EntityOrPartialMessage[A]] =
        _.evalMap { msg =>
          val loaded =
            msg.payload
              .traverse(fetchEntityOrPartialById)
              .map(_.flatten.map(e => e.id -> e).toMap)

          loaded.map(docs => EntityOrPartialMessage(msg, docs))
        }

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
