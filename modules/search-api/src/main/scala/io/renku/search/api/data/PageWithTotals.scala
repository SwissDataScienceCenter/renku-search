package io.renku.search.api.data

import io.bullet.borer.NullOptions.given
import io.bullet.borer.derivation.MapBasedCodecs
import io.bullet.borer.{Decoder, Encoder}

final case class PageWithTotals(
    page: PageDef,
    totalResult: Long,
    totalPages: Int,
    prevPage: Option[Int] = None,
    nextPage: Option[Int] = None
)

object PageWithTotals:
  given Encoder[PageWithTotals] = MapBasedCodecs.deriveEncoder
  given Decoder[PageWithTotals] = MapBasedCodecs.deriveDecoder

  def apply(page: PageDef, totalResults: Long, hasMore: Boolean): PageWithTotals =
    PageWithTotals(
      page,
      totalResults,
      math.ceil(totalResults.toDouble / page.limit).toInt,
      Option(page.page - 1).filter(_ > 0),
      Option(page.page + 1).filter(_ => hasMore)
    )
