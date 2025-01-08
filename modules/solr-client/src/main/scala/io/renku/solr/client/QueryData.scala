package io.renku.solr.client

import io.bullet.borer.Encoder
import io.bullet.borer.derivation.MapBasedCodecs.deriveEncoder
import io.renku.solr.client.SolrSort.Direction
import io.renku.solr.client.facet.Facets
import io.renku.solr.client.schema.FieldName

final case class QueryData(
    query: String,
    filter: Seq[String],
    limit: Int,
    offset: Int,
    fields: Seq[FieldName] = Seq.empty,
    sort: SolrSort = SolrSort.empty,
    params: Map[String, String] = Map.empty,
    facet: Facets = Facets.empty
):
  def nextPage: QueryData =
    copy(offset = offset + limit)

  def withSort(sort: SolrSort): QueryData = copy(sort = sort)
  def appendSort(field: FieldName, dir: Direction = Direction.Asc): QueryData =
    copy(sort = sort + (field -> dir))
  def withFields(field: FieldName*) = copy(fields = field)
  def withFilter(fq: Seq[String]): QueryData = copy(filter = fq)
  def addFilter(q: String*): QueryData = copy(filter = filter ++ q)
  def withFacet(facet: Facets): QueryData = copy(facet = facet)
  def withLimit(limit: Int): QueryData = copy(limit = limit)
  def withOffset(offset: Int): QueryData = copy(offset = offset)
  def withCursor(cursorMark: CursorMark): QueryData =
    copy(params = params.updated("cursorMark", cursorMark.render))

  /** When using a cursor, it is required to add a `uniqueKey`field to the sort clause to
    * guarantee a deterministic order.
    */
  def withCursor(cursorMark: CursorMark, keyField: FieldName): QueryData =
    copy(
      params = params.updated("cursorMark", cursorMark.render),
      sort = sort + (keyField -> SolrSort.Direction.Asc)
    )

  def addSubQuery(field: FieldName, sq: SubQuery): QueryData =
    copy(
      params = params ++ sq.toParams(field),
      fields = fields :+ FieldName(s"${field.name}:[subquery]")
    )

object QueryData:

  def apply(query: QueryString): QueryData =
    QueryData(
      query.q,
      Nil,
      query.limit,
      query.offset,
      Nil,
      SolrSort.empty,
      Map.empty,
      Facets.empty
    )

  given Encoder[QueryData] = deriveEncoder
