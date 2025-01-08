package io.renku.avro.codec

import io.renku.avro.codec.encoders.RecordEncoders
import org.apache.avro.Schema

import scala.deriving.Mirror

trait AvroEncoder[T] { self =>

  def encode(schema: Schema): T => Any

  final def contramap[U](f: U => T): AvroEncoder[U] =
    AvroEncoder.curried[U](schema => u => self.encode(schema).apply(f(u)))

}

object AvroEncoder:
  def apply[T](f: (Schema, T) => Any): AvroEncoder[T] = (schema: Schema) => f(schema, _)
  def curried[T](f: Schema => T => Any): AvroEncoder[T] = (schema: Schema) => f(schema)
  def basic[T](f: T => Any): AvroEncoder[T] = (_: Schema) => t => f(t)
  def id[T]: AvroEncoder[T] = AvroEncoder.basic(identity)

  def apply[T](using enc: AvroEncoder[T]): AvroEncoder[T] = enc

  final inline def derived[A <: Product](using
      inline A: Mirror.ProductOf[A]
  ): AvroEncoder[A] =
    RecordEncoders.derived[A]
