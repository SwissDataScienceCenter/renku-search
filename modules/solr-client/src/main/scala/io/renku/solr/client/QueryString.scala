package io.renku.solr.client

final case class QueryString(q: String, limit: Int, offset: Int)

object QueryString:
  def apply(q: String): QueryString = QueryString(q, 50, 0)
