package io.renku.avro.codec.encoders

import io.renku.avro.codec.{AvroDecoder, AvroEncoder}
import io.renku.avro.codec.encoders.all.given
import io.renku.avro.codec.decoders.all.given
import munit.FunSuite
import org.apache.avro.SchemaBuilder
import org.apache.avro.generic.GenericFixed
import org.apache.avro.util.Utf8
import scodec.bits.ByteVector

class StringEncodersTest extends FunSuite {
  case class Foo(s: String) derives AvroDecoder, AvroEncoder

  test("encode strings") {
    val schema = SchemaBuilder
      .record("Foo")
      .fields()
      .name("s")
      .`type`("string")
      .noDefault()
      .endRecord()

    val record = AvroEncoder[Foo].encode(schema).apply(Foo("hello"))
    assertEquals(record, AvroRecord(schema, Seq(new Utf8("hello"))))
  }

  test("encode fixed strings") {
    val schema = SchemaBuilder.fixed("s").size(8)
    val res = AvroEncoder[String].encode(schema)("hello").asInstanceOf[GenericFixed]
    val data = ByteVector.view(res.bytes())
    assertEquals(data, ByteVector('h', 'e', 'l', 'l', 'o', 0, 0, 0))
  }
}
