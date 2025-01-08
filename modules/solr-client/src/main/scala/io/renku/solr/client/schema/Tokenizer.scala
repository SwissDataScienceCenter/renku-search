package io.renku.solr.client.schema

final case class Tokenizer(name: String)

object Tokenizer:
  val standard: Tokenizer = Tokenizer("standard")
  val whitespace: Tokenizer = Tokenizer("whitespace")
  val classic: Tokenizer = Tokenizer("classic")

  // https://solr.apache.org/guide/solr/latest/indexing-guide/tokenizers.html#uax29-url-email-tokenizer
  val uax29UrlEmail: Tokenizer = Tokenizer("uax29UrlEmail")

  val icu: Tokenizer = Tokenizer("icu")
  val openNlp: Tokenizer = Tokenizer("openNlp")
