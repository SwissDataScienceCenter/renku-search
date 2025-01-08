package io.renku.search.api.data

import io.bullet.borer.Decoder
import io.bullet.borer.Encoder
import io.bullet.borer.derivation.MapBasedCodecs

final case class PageDef(
    limit: Int,
    offset: Int
):
  require(limit > 0, "limit must be >0")
  require(offset >= 0, "offset must be positive")

  val page: Int =
    1 + (offset / limit)

object PageDef:
  val default: PageDef = PageDef(25, 0)

  def fromPage(pageNum: Int, perPage: Int): PageDef =
    PageDef(perPage, (pageNum - 1).abs * perPage)

  given Encoder[PageDef] = MapBasedCodecs.deriveEncoder
  given Decoder[PageDef] = MapBasedCodecs.deriveDecoder
