package io.renku.avro.codec.decoders

import io.renku.avro.codec.{AvroCodecException, AvroDecoder}

trait EitherDecoders {

  given [A, B](using
      da: AvroDecoder[A],
      db: AvroDecoder[B],
      ta: TypeGuardedDecoding[A],
      tb: TypeGuardedDecoding[B]
  ): AvroDecoder[Either[A, B]] =
    AvroDecoder.curried[Either[A, B]] { schema =>
      require(schema.isUnion)
      require(schema.getTypes.size() == 2)

      val leftSchema = schema.getTypes.get(0)
      val rightSchema = schema.getTypes.get(1)

      { value =>
        if (ta.guard(leftSchema).isDefinedAt(value)) Left(da.decode(schema)(value))
        else if (tb.guard(rightSchema).isDefinedAt(value))
          Right(db.decode(schema)(value))
        else {
          val nameA = leftSchema.getFullName
          val nameB = rightSchema.getFullName
          throw AvroCodecException.decode(
            s"Could not decode $value into Either[$nameA, $nameB]"
          )
        }
      }
    }
}
