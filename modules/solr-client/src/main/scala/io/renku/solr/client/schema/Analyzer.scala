package io.renku.solr.client.schema

// see https://solr.apache.org/guide/solr/latest/indexing-guide/analyzers.html
// https://solr.apache.org/guide/solr/latest/indexing-guide/schema-api.html#add-a-new-field-type

final case class Analyzer(
    tokenizer: Tokenizer,
    filters: Seq[Filter] = Nil
)

object Analyzer:
  def create(tokenizer: Tokenizer, filters: Filter*): Analyzer =
    Analyzer(tokenizer, filters)

  val classic: Analyzer = Analyzer(Tokenizer.classic, filters = List(Filter.classic))

  val defaultSearch: Analyzer = Analyzer(
    tokenizer = Tokenizer.uax29UrlEmail,
    filters = Seq(
      Filter.lowercase,
      Filter.stop,
      Filter.englishMinimalStem,
      Filter.asciiFolding
    )
  )
