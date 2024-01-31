package io.renku.avro.codec

import io.renku.avro.codec.AvroAlternativeReaderTest.Person
import io.renku.avro.codec.all.given
import munit.FunSuite
import org.apache.avro.{AvroRuntimeException, Schema, SchemaBuilder}
import scodec.bits.ByteVector

class AvroAlternativeReaderTest extends FunSuite:
  private val avro = AvroIO(Person.schema)

  test("read binary-then-json for record"):
    val persons = Seq(Person("James", 30), Person("Jane", 28))
    val binary = avro.write(persons)
    val json = avro.writeJson(persons)
    val r0 = AvroAlternativeReader.readBinaryOrJson[Person](binary, avro)
    val r1 = AvroAlternativeReader.readBinaryOrJson[Person](json, avro)
    assertEquals(r0, persons)
    assertEquals(r1, persons)

  test("read binary-then-json single double"):
    val intAvro = AvroIO(SchemaBuilder.builder().doubleType())
    val binary = intAvro.write(Seq(42d))
    val json = intAvro.writeJson(Seq(42d))
    val r0 = AvroAlternativeReader.readBinaryOrJson[Double](binary, intAvro)
    val r1 = AvroAlternativeReader.readBinaryOrJson[Double](json, intAvro)
    assertEquals(r0, Seq(42d))
    assertEquals(r1, Seq(42d))

  test("read binary-then-json for doubles".ignore):
    val intAvro = AvroIO(SchemaBuilder.builder().doubleType())
    val binary = intAvro.write(Seq(42d, 64d))
    val json = intAvro.writeJson(Seq(42d, 64d))
    val r0 = AvroAlternativeReader.readBinaryOrJson[Double](binary, intAvro)
    val r1 = AvroAlternativeReader.readBinaryOrJson[Double](json, intAvro)
    assertEquals(r0, Seq(42d, 64d))
    assertEquals(r1, Seq(42d, 64d))

  test("read binary-then-json for longs".ignore):
    val intAvro = AvroIO(SchemaBuilder.builder().longType())
    val binary = intAvro.write(Seq(42L))
    val json = intAvro.writeJson(Seq(42L))
    val r0 = AvroAlternativeReader.readBinaryOrJson[Long](binary, intAvro)
    val r1 = AvroAlternativeReader.readBinaryOrJson[Long](json, intAvro)
    assertEquals(r0, Seq(42L))
    assertEquals(r1, Seq(42L))

  test("read binary and json failing for records"):
    val badInput1 = ByteVector.encodeUtf8("{}").toOption.get
    val badInput2 = ByteVector.fromValidHex("a1b2")
    intercept[AvroRuntimeException] {
      println(AvroAlternativeReader.readBinaryOrJson[Int](badInput1, avro))
    }
    intercept[AvroRuntimeException] {
      println(AvroAlternativeReader.readBinaryOrJson[Person](badInput2, avro))
    }

object AvroAlternativeReaderTest:
  final case class Person(name: String, age: Int) derives AvroEncoder, AvroDecoder
  object Person:
    //format: off
    val schema: Schema = SchemaBuilder
      .record("Person").fields()
      .name("name").`type`("string").noDefault()
      .name("age").`type`("int").noDefault()
      .endRecord()
    //format: on
