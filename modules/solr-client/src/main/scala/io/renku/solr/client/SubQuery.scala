package io.renku.solr.client

import cats.syntax.option.*

import io.renku.solr.client.schema.FieldName

final case class SubQuery(
    query: String,
    filter: String,
    limit: Int,
    offset: Int = 0,
    fields: Seq[FieldName] = Seq.empty,
    sort: SolrSort = SolrSort.empty
):
  def withSort(sort: SolrSort): SubQuery = copy(sort = sort)
  def withFields(field: FieldName*) = copy(fields = field)
  def withFilter(q: String): SubQuery = copy(filter = filter)
  def withLimit(limit: Int): SubQuery = copy(limit = limit)
  def withOffset(offset: Int): SubQuery = copy(offset = offset)

  private[client] def toParams(field: FieldName): Map[String, String] =
    def key(s: String): String = s"${field.name}.$s"
    List(
      (key("q") -> query).some,
      Option.when(filter.nonEmpty)(key("fq") -> filter),
      Option.when(limit >= 0)(key("limit") -> limit.toString),
      Option.when(offset > 0)(key("offset") -> offset.toString),
      Option.when(fields.nonEmpty)(key("fl") -> fields.mkString(",")),
      Option.when(sort.nonEmpty)(key("sort") -> sort.toSolr)
    ).collect { case Some(p) => p }.toMap
