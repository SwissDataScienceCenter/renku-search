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

package io.renku.search.provision.metrics

import cats.MonadThrow
import cats.syntax.all.*
import io.renku.search.solr.client.SearchSolrClient
import io.renku.search.solr.documents.DocumentKind
import io.renku.search.solr.query.SolrToken
import io.renku.search.solr.query.SolrToken.entityTypeIs
import io.renku.search.solr.schema.EntityDocumentSchema.Fields
import io.renku.solr.client.QueryData
import io.renku.solr.client.facet.{Facet, Facets}
import io.renku.solr.client.schema.FieldName

private class DocumentKindGaugeUpdater[F[_]: MonadThrow](
    sc: SearchSolrClient[F],
    gauge: DocumentKindGauge
):

  private val kindsField = FieldName("kinds")
  private val queryData =
    QueryData(
      entityTypeIs(gauge.entityType).value,
      filter = Seq.empty,
      limit = 0,
      offset = 0,
      facet = Facets(Facet.Terms(kindsField, Fields.kind))
    )

  def update(): F[Unit] =
    sc.query[Null](queryData)
      .flatMap {
        _.facetResponse
          .flatMap(_.buckets.get(kindsField))
          .map(_.buckets.toList)
          .sequence
          .flatten
          .map(b => toDocumentKind(b.value).tupleRight(b.count.toDouble))
          .sequence
          .map(addMissingKinds)
          .map(_.foreach { case (k, c) => gauge.set(k, c) })
      }

  private def toDocumentKind(v: String): F[DocumentKind] =
    MonadThrow[F]
      .fromEither(DocumentKind.fromString(v).leftMap(new IllegalArgumentException(_)))

  private def addMissingKinds(
      in: List[(DocumentKind, Double)]
  ): List[(DocumentKind, Double)] =
    DocumentKind.values
      .foldLeft(in.toMap)((out, k) => out.updatedWith(k)(_.orElse(0d.some)))
      .toList
