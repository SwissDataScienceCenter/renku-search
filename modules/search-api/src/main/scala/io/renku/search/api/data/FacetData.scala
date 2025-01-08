package io.renku.search.api.data

import io.bullet.borer.Decoder
import io.bullet.borer.Encoder
import io.bullet.borer.derivation.MapBasedCodecs
import io.renku.search.model.EntityType

final case class FacetData(
    entityType: Map[EntityType, Int]
)

object FacetData:
  val empty: FacetData = FacetData(Map.empty)

  given Decoder[FacetData] = MapBasedCodecs.deriveDecoder
  given Encoder[FacetData] = MapBasedCodecs.deriveEncoder
