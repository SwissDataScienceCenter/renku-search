package io.renku.avro.codec

sealed abstract class AvroCodecException(msg: String) extends RuntimeException(msg)

object AvroCodecException:
  def encode(msg: String): AvroEncodeError = AvroEncodeError(msg)
  def decode(msg: String): AvroDecodeError = AvroDecodeError(msg)

  final class AvroEncodeError(msg: String) extends AvroCodecException(msg)

  final class AvroDecodeError(msg: String) extends AvroCodecException(msg)
