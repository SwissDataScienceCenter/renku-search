package io.renku.avro.codec.json

import io.renku.avro.codec.{AvroEncoder, AvroWriter}
import io.renku.avro.codec.encoders.all.given
import org.apache.avro.{Schema, SchemaBuilder}
import scodec.bits.ByteVector

trait AvroJsonEncoder[A]:
  def encode(value: A): ByteVector
  final def contramap[B](f: B => A): AvroJsonEncoder[B] =
    AvroJsonEncoder(f.andThen(this.encode))

object AvroJsonEncoder:
  def apply[A](using e: AvroJsonEncoder[A]): AvroJsonEncoder[A] = e

  def apply[A](f: A => ByteVector): AvroJsonEncoder[A] =
    (a: A) => f(a)

  def create[A: AvroEncoder](schema: Schema): AvroJsonEncoder[A] =
    a => AvroWriter(schema).writeJson(Seq(a))

  given AvroJsonEncoder[String] =
    create[String](SchemaBuilder.builder().stringType())

  given AvroJsonEncoder[Long] =
    create[Long](SchemaBuilder.builder().longType())

  given AvroJsonEncoder[Int] =
    create[Int](SchemaBuilder.builder().intType())

  given AvroJsonEncoder[Double] =
    create[Double](SchemaBuilder.builder().doubleType())
