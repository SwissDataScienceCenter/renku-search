package io.renku.json

import io.bullet.borer.*
import io.bullet.borer.derivation.MapBasedCodecs
import io.bullet.borer.derivation.MapBasedCodecs.deriveDecoder
import io.bullet.borer.derivation.key
import io.renku.json.JsonEncodingSpec.{Animal, Room}
import munit.FunSuite

class JsonEncodingSpec extends FunSuite {

  test("test with discriminator"):
    val r = Room("meeting room", 59)
    val json = Json.encode(r).toUtf8String
    val rr = Json.decode(json.getBytes).to[Room].value
    assertEquals(json, """{"_type":"Room","name":"meeting room","seats":59}""")
    assertEquals(rr, r)

  test("encode with key annotation"):
    val c = JsonEncodingSpec.Course("my course", 25)
    val json = Json.encode(c).toUtf8String
    assertEquals(json, """{"type":"Course","name":"my course","price":25}""")

  test("encode sealed hierarchies"):
    val dog: Animal = Animal.Dog("bello")
    val cat: Animal = Animal.Cat("maunzi")
    val dogJson = Json.encode(dog).toUtf8String
    val catJson = Json.encode(cat).toUtf8String
    val cat0 = Json.decode(catJson.getBytes).to[Animal].value
    assertEquals(cat0, cat)
    val dog0 = Json.decode(dogJson.getBytes).to[Animal].value
    assertEquals(dog0, dog)
}

object JsonEncodingSpec:
  case class Room(name: String, seats: Int)
  object Room:
    given Decoder[Room] = deriveDecoder
    given Encoder[Room] = EncoderSupport.deriveWithDiscriminator[Room]("_type")

  case class Course(name: String, @key("price") dollars: Int)
  object Course:
    given Encoder[Course] = EncoderSupport.deriveWithDiscriminator[Course]("type")

  sealed trait Animal
  object Animal:
    given AdtEncodingStrategy =
      AdtEncodingStrategy.flat(typeMemberName = "type")
    given Decoder[Animal] = MapBasedCodecs.deriveAllDecoders[Animal]
    given Encoder[Animal] = EncoderSupport.derive[Animal]

    final case class Dog(name: String) extends Animal
    object Dog:
      given Encoder[Dog] = EncoderSupport.deriveWithDiscriminator[Dog]("type")

    final case class Cat(name: String) extends Animal
    object Cat:
      given Encoder[Cat] = EncoderSupport.deriveWithDiscriminator[Cat]("type")
