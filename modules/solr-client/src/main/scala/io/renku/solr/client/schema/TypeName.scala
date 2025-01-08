package io.renku.solr.client.schema

import io.bullet.borer.Decoder
import io.bullet.borer.Encoder

opaque type TypeName = String

object TypeName:
  def apply(name: String): TypeName = name

  extension (self: TypeName) def name: String = self

  given Encoder[TypeName] = Encoder.forString
  given Decoder[TypeName] = Decoder.forString
