package io.renku.avro.codec

import io.renku.avro.codec.encoders.all.given
import io.renku.avro.codec.decoders.all.given
import munit.FunSuite
import org.apache.avro.SchemaBuilder
import org.apache.avro.file.{CodecFactory, DataFileWriter}
import org.apache.avro.generic.GenericDatumWriter
import scodec.bits.ByteVector

import java.io.ByteArrayOutputStream

class AvroReaderTest extends FunSuite {

  case class Foo(name: String, age: Int) derives AvroDecoder, AvroEncoder
  val fooSchema = SchemaBuilder
    .record("Foo")
    .fields()
    .name("name")
    .`type`("string")
    .noDefault()
    .name("age")
    .`type`("int")
    .noDefault()
    .endRecord()

  test("test read single") {
    val data = Foo("eddi", 55)
    val wire = createData(data)
    val (result, pos) = AvroReader(fooSchema).read[Foo](wire).toOption.get
    assertEquals(result, List(data))
    assertEquals(pos, None)
  }

  test("read return remaining position") {
    val data = Foo("eddi", 56)
    val wire = createData(data) ++ createData(data).take(5)
    val result = AvroReader(fooSchema).read[Foo](wire)
    println(result)

  }

  def createData(foo: Foo) = {
    val writer = new GenericDatumWriter[Any](fooSchema)
    val dw = new DataFileWriter[Any](writer)
    val baos = new ByteArrayOutputStream()
    dw.setCodec(CodecFactory.bzip2Codec())
    dw.create(fooSchema, baos)
    val encoded = AvroEncoder[Foo].encode(fooSchema).apply(foo)
    dw.append(encoded)
    dw.close()
    ByteVector.view(baos.toByteArray)
  }
}
