package io.renku.search.query.docs

object SearchQueryManual {

  lazy val markdown: String =
    scala.io.Source.fromURL(getClass.getResource("/query-manual/manual.md")).mkString

}
