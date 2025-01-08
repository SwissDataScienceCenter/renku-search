package io.renku.avro.codec

import org.apache.avro.Schema
import org.apache.avro.file.DataFileReader
import org.apache.avro.generic.GenericDatumReader
import org.apache.avro.io.{
  BinaryDecoder,
  DatumReader,
  Decoder,
  DecoderFactory,
  JsonDecoder
}
import scodec.bits.ByteVector

import java.io.EOFException

trait AvroReader:
  def read[T: AvroDecoder](input: ByteVector): Seq[T]
  def readJson[T: AvroDecoder](input: ByteVector): Seq[T]
  def readContainer[T: AvroDecoder](input: ByteVector): Seq[T]

object AvroReader:
  def apply(schema: Schema): AvroReader = new Impl(schema)

  private class Impl(schema: Schema) extends AvroReader:
    private val reader = new GenericDatumReader[Any](schema)

    extension (self: DatumReader[Any])
      def readOpt[A: AvroDecoder](decoder: Decoder): Option[A] =
        try Option(self.read(null, decoder)).map(AvroDecoder[A].decode(schema))
        catch {
          case _: EOFException => None
        }

    override def read[T: AvroDecoder](
        input: ByteVector
    ): Seq[T] = {
      val in = ByteVectorInput(input)
      val decoder = DecoderFactory.get().binaryDecoder(in, null)
      read0(decoder)
    }

    def readJson[T: AvroDecoder](input: ByteVector): Seq[T] = {
      val in = ByteVectorInput(input)
      val decoder = DecoderFactory.get().jsonDecoder(schema, in)
      read0(decoder)
    }

    def read0[T: AvroDecoder](
        decoder: BinaryDecoder | JsonDecoder
    ): Seq[T] =
      @annotation.tailrec
      def go(r: GenericDatumReader[Any], result: List[T]): Seq[T] =
        if (isEnd(decoder)) result.reverse
        else
          r.readOpt(decoder) match
            case None     => result.reverse
            case Some(el) => go(r, el :: result)

      go(reader, Nil)

    private def isEnd(d: JsonDecoder | BinaryDecoder): Boolean = d match
      case jd: JsonDecoder   => false
      case bd: BinaryDecoder => bd.isEnd

    def readContainer[T: AvroDecoder](input: ByteVector): Seq[T] =
      val sin = ByteVectorInput(input)

      @annotation.tailrec
      def go(r: DataFileReader[Any], result: List[T]): Seq[T] =
        if (r.hasNext) {
          val data = r.next()
          val decoded = AvroDecoder[T].decode(schema)(data)
          go(r, decoded :: result)
        } else result.reverse

      go(new DataFileReader[Any](sin, reader), Nil)
