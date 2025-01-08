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
