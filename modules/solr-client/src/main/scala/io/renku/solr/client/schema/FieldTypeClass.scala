package io.renku.solr.client.schema

import io.bullet.borer.Decoder
import io.bullet.borer.Encoder

opaque type FieldTypeClass = String

object FieldTypeClass:
  def apply(name: String): FieldTypeClass = name

  extension (self: FieldTypeClass) def name: String = self

  object Defaults:
    val intPointField: FieldTypeClass = "IntPointField"
    val longPointField: FieldTypeClass = "LongPointField"
    val floatPointField: FieldTypeClass = "FloatPointField"
    val doublePointField: FieldTypeClass = "DoublePointField"
    val textField: FieldTypeClass = "TextField"
    val strField: FieldTypeClass = "StrField"
    val uuidField: FieldTypeClass = "UUIDField"
    val rankField: FieldTypeClass = "RankField"
    val datePointField: FieldTypeClass = "DatePointField"
    val dateRangeField: FieldTypeClass = "DateRangeField"
    val boolField: FieldTypeClass = "BoolField"
    val binaryField: FieldTypeClass = "BinaryField"
    val nestedPath: FieldTypeClass = "solr.NestPathField"

  given Encoder[FieldTypeClass] = Encoder.forString
  given Decoder[FieldTypeClass] = Decoder.forString
