package io.renku.avro.codec.json

import io.renku.avro.codec.{AvroDecoder, AvroReader}
import org.apache.avro.Schema
import scodec.bits.ByteVector

import scala.util.Try

trait AvroJsonDecoder[A]:
  def decode(json: ByteVector): Either[String, A]
  final def map[B](f: A => B): AvroJsonDecoder[B] =
    AvroJsonDecoder(this.decode.andThen(_.map(f)))
  final def emap[B](f: A => Either[String, B]): AvroJsonDecoder[B] =
    AvroJsonDecoder(this.decode.andThen(_.flatMap(f)))

object AvroJsonDecoder:
  def apply[A](using d: AvroJsonDecoder[A]): AvroJsonDecoder[A] = d

  def apply[A](f: ByteVector => Either[String, A]): AvroJsonDecoder[A] =
    (json: ByteVector) => f(json)

  def create[A: AvroDecoder](schema: Schema): AvroJsonDecoder[A] = { json =>
    Try(AvroReader(schema).readJson[A](json)).toEither.left
      .map(_.getMessage)
      .flatMap(_.headOption.toRight(s"Empty json"))
  }
