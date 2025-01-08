package io.renku.search.api.data

import cats.Show

import io.renku.search.query.Query

final case class QueryInput(
    query: Query,
    page: PageDef
)

object QueryInput:
  given Show[QueryInput] = Show.show(i => s"(${i.query.render}, ${i.page})")

  def pageOne(query: Query): QueryInput =
    QueryInput(query, PageDef.default)
