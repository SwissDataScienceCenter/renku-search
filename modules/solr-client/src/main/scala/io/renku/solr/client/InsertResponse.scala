package io.renku.solr.client

import io.bullet.borer.Decoder
import io.bullet.borer.derivation.MapBasedCodecs.deriveDecoder

final case class InsertResponse(responseHeader: ResponseHeader)

object InsertResponse:
  given Decoder[InsertResponse] = deriveDecoder
