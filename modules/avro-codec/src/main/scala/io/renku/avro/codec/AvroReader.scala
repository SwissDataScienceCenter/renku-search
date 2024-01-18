package io.renku.avro.codec

import org.apache.avro.Schema
import org.apache.avro.file.{DataFileReader, SeekableByteArrayInput}
import org.apache.avro.generic.GenericDatumReader
import scodec.bits.ByteVector

import scala.util.Using

trait AvroReader:
  def read[T: AvroDecoder](input: ByteVector): Either[Throwable, (Seq[T], Option[Long])]

object AvroReader:
  def apply(schema: Schema): AvroReader = new Impl(schema)

  private class Impl(schema: Schema) extends AvroReader:
    private[this] val reader = new GenericDatumReader[Any](schema)

    override def read[T: AvroDecoder](
        input: ByteVector
    ): Either[Throwable, (Seq[T], Option[Long])] =
      val sin = new SeekableByteArrayInput(input.toArray)
      val len = sin.length()

      @annotation.tailrec
      def go(r: DataFileReader[Any], pos: Long, result: List[T]): (Seq[T], Option[Long]) =
        if (r.hasNext) {
          val newPos = sin.tell()
          val data = r.next()
          val decoded = AvroDecoder[T].decode(schema)(data)
          go(r, newPos, decoded :: result)
        } else {
          println(s"pos=$pos  len=${sin.length()}")
          (result, Option(pos).filter(_ < len))
        }

      Using(new DataFileReader[Any](sin, reader))(go(_, 0, Nil)).toEither
