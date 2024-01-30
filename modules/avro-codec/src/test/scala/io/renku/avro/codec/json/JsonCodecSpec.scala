package io.renku.avro.codec.json

import io.renku.avro.codec.*
import io.renku.avro.codec.all.given
import munit.FunSuite
import org.apache.avro.{Schema, SchemaBuilder}

import scala.collection.immutable.Map

class JsonCodecSpec extends FunSuite {

  test("encode and decode json"):
    val person =
      JsonCodecSpec.Person("hugo", 42, Map("date" -> "1982", "children" -> "0"))
    val json = AvroJsonEncoder[JsonCodecSpec.Person].encode(person)
    val decoded = AvroJsonDecoder[JsonCodecSpec.Person].decode(json)
    assertEquals(decoded, Right(person))

  test("encode and decode lists"):
    val person1 =
      JsonCodecSpec.Person("hugo1", 41, Map("date" -> "1981", "children" -> "1"))
    val person2 =
      JsonCodecSpec.Person("hugo1", 42, Map("date" -> "1982", "children" -> "2"))
    val persons = person1 :: person2 :: Nil

    val json = AvroJsonEncoder
      .encodeList[JsonCodecSpec.Person](JsonCodecSpec.Person.schema)
      .encode(persons)
    val decoded = AvroJsonDecoder
      .decodeList[JsonCodecSpec.Person](JsonCodecSpec.Person.schema)
      .decode(json)

    assertEquals(decoded, Right(persons))
}

object JsonCodecSpec:

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
