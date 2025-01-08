package io.renku.solr.client.schema

import io.bullet.borer.derivation.MapBasedCodecs
import io.bullet.borer.derivation.key
import io.bullet.borer.{Decoder, Encoder}

final case class Field(
    name: FieldName,
    @key("type") typeName: TypeName,
    required: Boolean = false,
    indexed: Boolean = true,
    stored: Boolean = true,
    multiValued: Boolean = false,
    uninvertible: Boolean = true,
    docValues: Boolean = false
):
  def makeMultiValued: Field = copy(multiValued = true)

object Field:
  def apply(name: FieldName, fieldType: FieldType): Field =
    apply(name, fieldType.name)

  given Encoder[Field] = MapBasedCodecs.deriveEncoder
  given Decoder[Field] = MapBasedCodecs.deriveDecoder
