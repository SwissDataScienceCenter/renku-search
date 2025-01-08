package io.renku.avro.codec.encoders

import io.renku.avro.codec.AvroEncoder
import org.apache.avro.Schema

trait OptionEncoders:
  given [T](using e: AvroEncoder[T]): AvroEncoder[Option[T]] =
    OptionEncoders.ForOption[T](e)

object OptionEncoders:
  final class ForOption[T](encoder: AvroEncoder[T]) extends AvroEncoder[Option[T]] {
    def encode(schema: Schema): Option[T] => Any = {
      // nullables must be encoded with a union of 2 elements, where null is the first type
      require(
        schema.getType == Schema.Type.UNION,
        "Options can only be encoded with a UNION schema"
      )
      require(
        schema.getTypes.size() >= 2,
        "Options can only be encoded with a union schema with 2 or more types"
      )
      require(schema.getTypes.get(0).getType == Schema.Type.NULL)
      val schemaSize = schema.getTypes.size()
      val elementSchema = schemaSize match
        case 2 => schema.getTypes.get(1)
        case _ => Schema.createUnion(schema.getTypes.subList(1, schemaSize))
      val elementEncoder = encoder.encode(elementSchema)
      { option => option.fold(null)(value => elementEncoder(value)) }
    }
  }
