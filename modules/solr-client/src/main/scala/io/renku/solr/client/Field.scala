package io.renku.solr.client

import io.circe.{Decoder, Encoder}

opaque type Field = String
object Field:
  def apply(name: String): Field = name

  given Encoder[Field] = Encoder.encodeString
  given Decoder[Field] = Decoder.decodeString

  extension (self: Field) def name: String = self
