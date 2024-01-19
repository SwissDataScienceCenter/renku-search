package io.renku.avro.codec

import io.renku.avro.codec.decoders.all.given
import io.renku.avro.codec.encoders.all.given
import munit.FunSuite
import org.apache.avro.SchemaBuilder

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

  val avro = AvroIO(fooSchema)

  test("read/write single") {
    val data = Foo("eddi", 55)
    val wire = avro.write(Seq(data))
    val result = avro.read[Foo](wire)
    assertEquals(result, List(data))
  }

  test("read/write multiple values") {
    val values = (1 to 10).toList.map(n => Foo("eddi", 50 + n))
    val wire = avro.write(values)
    val result = avro.read[Foo](wire)
    assertEquals(result, values)
  }

  test("read/write container") {
    val values = (1 to 10).toList.map(n => Foo("eddi", 50 + n))
    val wire = avro.writeContainer(values)
    val result = avro.readContainer[Foo](wire)
    assertEquals(result, values)
  }
}
