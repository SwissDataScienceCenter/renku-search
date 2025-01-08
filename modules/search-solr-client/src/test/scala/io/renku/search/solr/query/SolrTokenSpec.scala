package io.renku.search.solr.query

import java.time.Instant

import io.renku.search.solr.schema.EntityDocumentSchema.Fields as SolrField
import munit.FunSuite

class SolrTokenSpec extends FunSuite:

  test("fold with parens"):
    assertEquals(
      List(
        SolrToken.fieldIs(SolrField.name, SolrToken.fromString("john")),
        SolrToken.fieldIs(SolrField.id, SolrToken.fromString("1"))
      ).foldAnd,
      SolrToken.unsafeFromString("(name:john AND id:1)")
    )
    assertEquals(
      List(
        SolrToken.fieldIs(SolrField.name, SolrToken.fromString("john")),
        SolrToken.fieldIs(SolrField.id, SolrToken.fromString("1"))
      ).foldOr,
      SolrToken.unsafeFromString("(name:john OR id:1)")
    )

  test("escape `:` in timestamps"):
    val i = Instant.now
    assertEquals(
      SolrToken.fromInstant(i),
      SolrToken.unsafeFromString(StringEscape.escape(i.toString, ":"))
    )
