package io.renku.search.api.data

import io.bullet.borer.Decoder
import io.bullet.borer.Encoder
import io.bullet.borer.derivation.MapBasedCodecs

final case class SearchResult(
    items: Seq[SearchEntity],
    facets: FacetData,
    pagingInfo: PageWithTotals
)

object SearchResult:
  given Encoder[SearchResult] = MapBasedCodecs.deriveEncoder
  given Decoder[SearchResult] = MapBasedCodecs.deriveDecoder
