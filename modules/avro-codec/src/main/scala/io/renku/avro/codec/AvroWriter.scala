package io.renku.avro.codec

import org.apache.avro.Schema
import org.apache.avro.file.{CodecFactory, DataFileWriter}
import org.apache.avro.generic.GenericDatumWriter
import org.apache.avro.io.EncoderFactory
import org.apache.avro.util.ByteBufferOutputStream
import scodec.bits.ByteVector

import scala.jdk.CollectionConverters.*

trait AvroWriter:
  def write[A: AvroEncoder](values: Seq[A]): ByteVector
  def writeContainer[A: AvroEncoder](values: Seq[A]): ByteVector

object AvroWriter:
  def apply(
      schema: Schema,
      codecFactory: CodecFactory = CodecFactory.nullCodec()
  ): AvroWriter =
    new Impl(schema, codecFactory)

  private class Impl(schema: Schema, cf: CodecFactory) extends AvroWriter:
    private[this] val writer = new GenericDatumWriter[Any](schema)

    def write[A: AvroEncoder](values: Seq[A]): ByteVector = {
      val encode = AvroEncoder[A].encode(schema)
      val baos = new ByteBufferOutputStream()
      val ef = EncoderFactory.get().binaryEncoder(baos, null)
      values.map(encode).foreach(writer.write(_, ef))
      ef.flush()

      baos.getBufferList.asScala
        .map(ByteVector.view)
        .foldLeft(ByteVector.empty)(_ ++ _)
    }

    def writeContainer[A: AvroEncoder](values: Seq[A]): ByteVector = {
      val encode = AvroEncoder[A].encode(schema)
      val baos = new ByteBufferOutputStream()
      val dw = new DataFileWriter[Any](writer)
      dw.setCodec(cf)
      dw.create(schema, baos)
      values
        .map(encode)
        .foreach(dw.append)
      dw.flush()
      baos.getBufferList.asScala
        .map(ByteVector.view)
        .foldLeft(ByteVector.empty)(_ ++ _)
    }
