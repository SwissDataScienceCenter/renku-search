package io.renku.solr.client

import io.bullet.borer.{Decoder, Encoder}

/** Allow paged results using a cursor as described here:
  * https://solr.apache.org/guide/solr/latest/query-guide/pagination-of-results.html#fetching-a-large-number-of-sorted-results-cursors
  */
enum CursorMark:
  case Start
  case Mark(value: String)

  def render: String = this match
    case Start   => "*"
    case Mark(v) => v

object CursorMark:

  given Encoder[CursorMark] =
    Encoder.forString.contramap(_.render)

  given Decoder[CursorMark] =
    Decoder.forString.map(s => if ("*" == s) CursorMark.Start else CursorMark.Mark(s))
