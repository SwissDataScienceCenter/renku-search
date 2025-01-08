package io.renku.search.solr.query

import munit.FunSuite

class StringEscapeSpec extends FunSuite:

  val specialChars = "\\+-!():^[]\"{}~*?|&;/ \t"
  def escape(s: String) = StringEscape.queryChars(s)

  test("escape query characters"):
    specialChars.foreach { c =>
      assertEquals(escape(s"a${c}b"), s"a\\${c}b")
    }

  test("escape given chars only"):
    assertEquals(StringEscape.escape("?:?", ":"), "?\\:?")
