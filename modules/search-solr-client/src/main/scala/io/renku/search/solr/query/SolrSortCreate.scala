package io.renku.search.solr.query

import cats.data.NonEmptyList

import io.renku.search.query.{Order, SortableField}
import io.renku.search.solr.schema.EntityDocumentSchema.Fields as SolrField
import io.renku.solr.client.SolrSort
import io.renku.solr.client.schema.FieldName

private object SolrSortCreate:
  private def fromField(field: SortableField): FieldName =
    field match
      case SortableField.Name    => SolrField.name
      case SortableField.Score   => SolrField.score
      case SortableField.Created => SolrField.creationDate

  private def fromDirection(d: Order.Direction): SolrSort.Direction =
    d match
      case Order.Direction.Asc  => SolrSort.Direction.Asc
      case Order.Direction.Desc => SolrSort.Direction.Desc

  def apply(ts: Order.OrderedBy*): SolrSort =
    SolrSort(ts.map(e => (fromField(e.field), fromDirection(e.direction)))*)

  def apply(ts: NonEmptyList[Order.OrderedBy]): SolrSort =
    apply(ts.toList*)
