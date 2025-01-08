package io.renku.avro.codec.decoders

import io.renku.avro.codec.{AvroCodecException, AvroDecoder}
import org.apache.avro.generic.GenericFixed
import scodec.bits.{BitVector, ByteVector}

import java.nio.ByteBuffer

trait ByteArrayDecoders:
  given AvroDecoder[ByteVector] = ByteArrayDecoders.forByteVector
  given AvroDecoder[BitVector] = ByteArrayDecoders.forByteVector.map(_.bits)
  given AvroDecoder[Array[Byte]] = ByteArrayDecoders.forByteArray

object ByteArrayDecoders:
  val forByteVector: AvroDecoder[ByteVector] = AvroDecoder.basic[ByteVector] {
    case b: ByteBuffer   => ByteVector.view(b)
    case b: Array[Byte]  => ByteVector.view(b)
    case f: GenericFixed => ByteVector.view(f.bytes())
    case v =>
      throw AvroCodecException.decode(
        s"ByteVectorDecoder cannot decode $v"
      )
  }

  val forByteArray: AvroDecoder[Array[Byte]] = AvroDecoder.basic[Array[Byte]] {
    case b: ByteBuffer   => b.array()
    case b: Array[Byte]  => b
    case f: GenericFixed => f.bytes()
    case v =>
      throw AvroCodecException.decode(
        s"ByteArrayDecoder cannot decode $v"
      )
  }
