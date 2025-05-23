package io.renku.avro.codec.encoders

import io.renku.avro.codec.AvroEncoder
import org.apache.avro.Schema

import scala.jdk.CollectionConverters.*
import scala.reflect.ClassTag

trait CollectionEncoders {
  private def iterableEncoder[T, C[X] <: Iterable[X]](
      encoder: AvroEncoder[T]
  ): AvroEncoder[C[T]] = (schema: Schema) => {
    require(schema.getType == Schema.Type.ARRAY, s"Expected array schema, got: $schema")
    val elementEncoder = encoder.encode(schema.getElementType)
    { t => t.map(elementEncoder.apply).toList.asJava }
  }

  given [T](using encoder: AvroEncoder[T], tag: ClassTag[T]): AvroEncoder[Array[T]] =
    (schema: Schema) => {
      require(schema.getType == Schema.Type.ARRAY)
      val elementEncoder = encoder.encode(schema.getElementType)
      { t => t.map(elementEncoder.apply).toList.asJava }
    }

  given [T](using encoder: AvroEncoder[T]): AvroEncoder[List[T]] = iterableEncoder(
    encoder
  )
  given [T](using encoder: AvroEncoder[T]): AvroEncoder[Seq[T]] = iterableEncoder(encoder)
  given [T](using encoder: AvroEncoder[T]): AvroEncoder[Set[T]] = iterableEncoder(encoder)
  given [T](using encoder: AvroEncoder[T]): AvroEncoder[Vector[T]] = iterableEncoder(
    encoder
  )
  given [T](using encoder: AvroEncoder[T]): AvroEncoder[Map[String, T]] =
    CollectionEncoders.MapEncoder[T](encoder)
}

object CollectionEncoders:
  private class MapEncoder[T](encoder: AvroEncoder[T])
      extends AvroEncoder[Map[String, T]]:
    override def encode(schema: Schema): Map[String, T] => Any = {
      val encodeT = encoder.encode(schema.getValueType)
      { value =>
        val map = new java.util.HashMap[String, Any]
        value.foreach { case (k, v) => map.put(k, encodeT.apply(v)) }
        map
      }
    }
