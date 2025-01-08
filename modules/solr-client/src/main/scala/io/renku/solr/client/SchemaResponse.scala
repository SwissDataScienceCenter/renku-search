package io.renku.solr.client

import io.bullet.borer.Decoder
import io.bullet.borer.derivation.MapBasedCodecs
import io.renku.solr.client.schema.*
import io.renku.solr.client.schema.SchemaJsonCodec.given

final case class SchemaResponse(
    responseHeader: ResponseHeader,
    schema: CoreSchema
)

object SchemaResponse:
  given Decoder[SchemaResponse] = MapBasedCodecs.deriveDecoder
