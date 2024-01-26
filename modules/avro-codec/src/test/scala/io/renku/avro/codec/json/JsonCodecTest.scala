package io.renku.avro.codec.json

import io.renku.avro.codec.*
import io.renku.avro.codec.all.given
import munit.FunSuite
import org.apache.avro.{Schema, SchemaBuilder}
import scala.collection.immutable.Map

class JsonCodecTest extends FunSuite {

  test("encode and decode json") {
    val person =
      JsonCodecTest.Person("hugo", 42, Map("date" -> "1982", "children" -> "0"))
    val json = AvroJsonEncoder[JsonCodecTest.Person].encode(person)
    val decoded = AvroJsonDecoder[JsonCodecTest.Person].decode(json)
    assertEquals(decoded, Right(person))
  }
}

object JsonCodecTest:

  case class Person(name: String, age: Int, props: Map[String, String])
      derives AvroEncoder,
        AvroDecoder
  object Person:
    val schema: Schema = SchemaBuilder
      .record("Person")
      .fields()
      .name("name")
      .`type`("string")
      .noDefault()
      .name("age")
      .`type`("int")
      .noDefault()
      .name("props")
      .`type`(
        SchemaBuilder.map().values(SchemaBuilder.builder().`type`("string"))
      )
      .noDefault()
      .endRecord()

    given AvroJsonEncoder[Person] = AvroJsonEncoder.create(schema)
    given AvroJsonDecoder[Person] = AvroJsonDecoder.create(schema)
