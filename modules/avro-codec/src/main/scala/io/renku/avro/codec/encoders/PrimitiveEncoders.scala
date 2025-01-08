package io.renku.avro.codec.encoders

import io.renku.avro.codec.AvroEncoder

trait PrimitiveEncoders:
  given AvroEncoder[Long] = AvroEncoder.basic(n => java.lang.Long.valueOf(n))
  given AvroEncoder[Int] = AvroEncoder.basic(n => java.lang.Integer.valueOf(n))
  given AvroEncoder[Short] = AvroEncoder.basic(n => java.lang.Short.valueOf(n))
  given AvroEncoder[Byte] = AvroEncoder.basic(n => java.lang.Byte.valueOf(n))
  given AvroEncoder[Double] = AvroEncoder.basic(n => java.lang.Double.valueOf(n))
  given AvroEncoder[Float] = AvroEncoder.basic(n => java.lang.Float.valueOf(n))
  given AvroEncoder[Boolean] = AvroEncoder.basic(n => java.lang.Boolean.valueOf(n))
