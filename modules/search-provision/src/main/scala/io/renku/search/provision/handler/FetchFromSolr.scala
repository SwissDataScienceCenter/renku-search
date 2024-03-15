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

import io.renku.search.model.Id
import io.renku.search.provision.handler.FetchFromSolr.MessageDocument
import io.renku.search.provision.handler.MessageReader.Message
import io.renku.search.solr.client.SearchSolrClient
import io.renku.search.solr.documents.Entity as Document
import io.renku.search.query.Query
import io.bullet.borer.derivation.MapBasedCodecs
import io.bullet.borer.Decoder
import io.renku.search.solr.schema.EntityDocumentSchema.Fields
import io.renku.solr.client.QueryString
import io.renku.solr.client.QueryData
import io.renku.search.solr.query.SolrToken
import io.renku.search.model.EntityType

trait FetchFromSolr[F[_]]:
  def fetch1[A](using IdExtractor[A]): Pipe[F, Message[A], MessageDocument[A]]
  def fetchProjectForUser(userId: Id): Stream[F, FetchFromSolr.ProjectId]

object FetchFromSolr:
  final case class ProjectId(id: Id)
  object ProjectId:
    given Decoder[ProjectId] = MapBasedCodecs.deriveDecoder

  final case class MessageDocument[A: IdExtractor](
      message: MessageReader.Message[A],
      documents: Map[Id, Document]
  ):
    def update(f: (A, Document) => Option[Document]): Message[Document] =
      Message(
        message.raw,
        message.decoded
          .map(a =>
            documents
              .get(IdExtractor[A].getId(a))
              .flatMap(doc => f(a, doc))
          )
          .flatten
      )

  def apply[F[_]: Sync](
      solrClient: SearchSolrClient[F]
  ): FetchFromSolr[F] =
    new FetchFromSolr[F] {
      val logger = scribe.cats.effect[F]

      private def idQuery(id: Id): Query =
        // TODO this must be renamed to "idIs" since we have only one id type
        Query(Query.Segment.projectIdIs(id.value))

      def fetchProjectForUser(userId: Id): Stream[F, FetchFromSolr.ProjectId] =
        val query = QueryString(
          List(
            SolrToken.fieldIs(
              Fields.entityType,
              SolrToken.fromEntityType(EntityType.Project)
            ),
            List(
              SolrToken.fieldIs(Fields.owners, SolrToken.fromId(userId)),
              SolrToken.fieldIs(Fields.members, SolrToken.fromId(userId))
            ).foldOr
          ).foldAnd.value
        )
        solrClient.queryAll[ProjectId](QueryData(query))

      def fetch1[A](using IdExtractor[A]): Pipe[F, Message[A], MessageDocument[A]] =
        _.evalMap { msg =>
          val ids = msg.decoded.map(IdExtractor[A].getId)
          val loaded = ids
            .traverse(id =>
              solrClient
                .queryEntity(idQuery(id), 1, 0)
                .map(_.responseBody.docs.headOption)
                .map(doc => id -> doc)
            )
            .flatTap { results =>
              val notFound = results.filter(_._2.isEmpty).map(_._1.value).mkString(", ")
              if (notFound.isEmpty) Sync[F].unit
              else
                logger.warn(
                  s"Document ids: '$notFound' for update doesn't exist in Solr; skipping"
                )
            }
            .map(_.foldLeft(Map.empty[Id, Document]) {
              case (result, (id, Some(doc))) => result.updated(id, doc)
              case (result, (id, None))      => result
            })

          loaded.map(m => MessageDocument(msg, m))
        }
    }
