package io.renku.avro.codec.encoders

import io.renku.avro.codec.AvroEncoder

trait EitherEncoders {

  given [A, B](using ea: AvroEncoder[A], eb: AvroEncoder[B]): AvroEncoder[Either[A, B]] =
    AvroEncoder.curried { schema =>
      require(schema.isUnion, s"Either must use a union schema. Got: ${schema.getType}")
      require(
        schema.getTypes.size() == 2,
        s"Either must use a UNION of two types. Got: ${schema.getTypes}"
      )

      {
        case Left(a)  => ea.encode(schema.getTypes.get(0))(a)
        case Right(b) => eb.encode(schema.getTypes.get(1))(b)
      }
    }

}
