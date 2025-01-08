package io.renku.solr.client.schema

import io.bullet.borer.Decoder
import io.bullet.borer.Encoder

opaque type FieldName = String
object FieldName:
  val all: FieldName = "*"
  val score: FieldName = "score"
  val id: FieldName = "id"

  def apply(name: String): FieldName = name

  extension (self: FieldName) def name: String = self

  given Encoder[FieldName] = Encoder.forString
  given Decoder[FieldName] = Decoder.forString
