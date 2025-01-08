package io.renku.avro.codec.decoders

import io.renku.avro.codec.{AvroCodecException, AvroDecoder}

trait PrimitiveDecoders {

  given AvroDecoder[Byte] = AvroDecoder.basic[Byte] {
    case b: Byte => b
    case v       => v.asInstanceOf[Int].byteValue()
  }

  given AvroDecoder[Short] = AvroDecoder.basic[Short] {
    case b: Byte  => b
    case s: Short => s
    case i: Int   => i.toShort
  }

  given AvroDecoder[Int] = AvroDecoder.basic[Int] {
    case b: Byte  => b
    case s: Short => s
    case i: Int   => i
    case other    => throw AvroCodecException.decode(s"Cannot convert $other to INT")
  }

  given AvroDecoder[Long] = AvroDecoder.basic[Long] {
    case b: Byte  => b
    case s: Short => s
    case i: Int   => i
    case l: Long  => l
    case other    => throw AvroCodecException.decode(s"Cannot convert $other to LONG")
  }

  given AvroDecoder[Double] = AvroDecoder.basic[Double] {
    case d: Double           => d
    case d: java.lang.Double => d
  }

  given AvroDecoder[Float] = AvroDecoder.basic[Float] {
    case d: Float           => d
    case d: java.lang.Float => d
  }

  given AvroDecoder[Boolean] = AvroDecoder.basic[Boolean] {
    _.asInstanceOf[Boolean]
  }
}
