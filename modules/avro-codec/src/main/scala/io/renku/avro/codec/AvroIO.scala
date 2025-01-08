package io.renku.avro.codec
import org.apache.avro.Schema
import scodec.bits.ByteVector

trait AvroIO extends AvroWriter with AvroReader

object AvroIO:
  def apply(schema: Schema): AvroIO =
    new AvroIO:
      private val reader = AvroReader(schema)
      private val writer = AvroWriter(schema)

      override def write[A: AvroEncoder](values: Seq[A]): ByteVector =
        writer.write(values)

      override def writeJson[A: AvroEncoder](values: Seq[A]): ByteVector =
        writer.writeJson(values)

      override def writeContainer[A: AvroEncoder](values: Seq[A]): ByteVector =
        writer.writeContainer(values)

      override def read[T: AvroDecoder](input: ByteVector): Seq[T] = reader.read(input)

      override def readJson[T: AvroDecoder](input: ByteVector): Seq[T] =
        reader.readJson(input)

      override def readContainer[T: AvroDecoder](input: ByteVector): Seq[T] =
        reader.readContainer(input)
