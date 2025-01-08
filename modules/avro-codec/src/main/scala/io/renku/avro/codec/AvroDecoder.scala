package io.renku.avro.codec

import io.renku.avro.codec.decoders.RecordDecoders
import org.apache.avro.Schema

import scala.deriving.Mirror

trait AvroDecoder[T] { self =>

  def decode(schema: Schema): Any => T

  final def map[U](f: T => U): AvroDecoder[U] =
    AvroDecoder.curried[U](schema => in => f(self.decode(schema).apply(in)))
}

object AvroDecoder:
  def apply[T](f: (Schema, Any) => T): AvroDecoder[T] = (schema: Schema) => f(schema, _)
  def curried[T](f: Schema => Any => T): AvroDecoder[T] = (schema: Schema) => f(schema)
  def basic[T](f: Any => T): AvroDecoder[T] = apply[T]((_, in) => f(in))

  def apply[T](using dec: AvroDecoder[T]): AvroDecoder[T] = dec

  inline def derived[A <: Product](using
      inline A: Mirror.ProductOf[A]
  ): AvroDecoder[A] =
    RecordDecoders.derived[A]
