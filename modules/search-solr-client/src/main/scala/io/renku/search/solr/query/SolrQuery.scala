package io.renku.search.solr.query

import cats.Monoid
import cats.syntax.all.*

import io.renku.search.query.Order
import io.renku.solr.client.SolrSort

final case class SolrQuery(
    query: SolrToken,
    sort: SolrSort
):
  def withQuery(q: SolrToken): SolrQuery = copy(query = q)
  def ++(next: SolrQuery): SolrQuery =
    SolrQuery(query && next.query, sort ++ next.sort)

  def emptyToAll: SolrQuery =
    if (query.isEmpty) SolrQuery(SolrToken.all, sort)
    else this

object SolrQuery:
  val empty: SolrQuery = SolrQuery(SolrToken.empty, SolrSort.empty)

  def apply(e: SolrToken): SolrQuery =
    SolrQuery(e, SolrSort.empty)

  def sort(order: Order): SolrQuery =
    SolrQuery(SolrToken.empty, SolrSortCreate(order.fields))

  given Monoid[SolrQuery] = Monoid.instance(empty, (a, b) => a ++ b)
