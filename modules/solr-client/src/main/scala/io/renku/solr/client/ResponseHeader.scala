package io.renku.solr.client

import io.bullet.borer.Decoder
import io.bullet.borer.derivation.MapBasedCodecs.deriveDecoder
import io.bullet.borer.derivation.key

final case class ResponseHeader(
    status: Int,
    @key("QTime") queryTime: Long,
    params: Map[String, String] = Map()
)

object ResponseHeader:
  val empty: ResponseHeader = ResponseHeader(0, 0, Map.empty)
  given Decoder[ResponseHeader] = deriveDecoder
