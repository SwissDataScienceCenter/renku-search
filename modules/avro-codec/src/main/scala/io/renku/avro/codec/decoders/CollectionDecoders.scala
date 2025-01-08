package io.renku.avro.codec.decoders

import io.renku.avro.codec.{AvroCodecException, AvroDecoder}
import org.apache.avro.Schema

import scala.jdk.CollectionConverters.*
import scala.reflect.ClassTag

trait CollectionDecoders:
  given [T: ClassTag](using decoder: AvroDecoder[T]): AvroDecoder[Array[T]] =
    CollectionDecoders.ArrayDecoder[T](decoder)

  given [T](using dec: AvroDecoder[T]): AvroDecoder[List[T]] =
    CollectionDecoders.iterableDecoder[T, List](dec, _.toList)

  given [T](using dec: AvroDecoder[T]): AvroDecoder[Seq[T]] =
    CollectionDecoders.iterableDecoder[T, Seq](dec, _.toSeq)

  given [T](using dec: AvroDecoder[T]): AvroDecoder[Set[T]] =
    CollectionDecoders.iterableDecoder[T, Set](dec, _.toSet)

  given [T](using dec: AvroDecoder[T]): AvroDecoder[Map[String, T]] =
    CollectionDecoders.MapDecoder[T](dec)

object CollectionDecoders:
  private class ArrayDecoder[T: ClassTag](decoder: AvroDecoder[T])
      extends AvroDecoder[Array[T]]:
    def decode(schema: Schema): Any => Array[T] = {
      require(
        schema.getType == Schema.Type.ARRAY,
        s"Require schema type ARRAY (was $schema)"
      )
      val decodeT = decoder.decode(schema.getElementType)
      {
        case array: Array[_]               => array.map(decodeT)
        case list: java.util.Collection[_] => list.asScala.map(decodeT).toArray
        case list: Iterable[_]             => list.map(decodeT).toArray
        case other =>
          throw AvroCodecException.decode(s"Unsupported array: $other")
      }
    }

  private class MapDecoder[T](decoder: AvroDecoder[T])
      extends AvroDecoder[Map[String, T]]:
    override def decode(schema: Schema): Any => Map[String, T] = {
      require(schema.getType == Schema.Type.MAP)
      val decode = decoder.decode(schema.getValueType)
      { case map: java.util.Map[_, _] =>
        map.asScala.toMap.map { case (k, v) => k.toString -> decode(v) }
      }
    }

  def iterableDecoder[T, C[X] <: Iterable[X]](
      decoder: AvroDecoder[T],
      build: Iterable[T] => C[T]
  ): AvroDecoder[C[T]] =
    AvroDecoder.curried[C[T]] { schema =>
      require(
        schema.getType == Schema.Type.ARRAY,
        s"Require schema type ARRAY (was $schema)"
      )
      val decodeT = decoder.decode(schema.getElementType)

      {
        case list: java.util.Collection[_] => build(list.asScala.map(decodeT))
        case list: Iterable[_]             => build(list.map(decodeT))
        case array: Array[_]               =>
          // converting array to Seq in order to avoid requiring ClassTag[T] as does arrayDecoder.
          build(array.toSeq.map(decodeT))
        case other =>
          throw AvroCodecException.decode(
            s"Unsupported collection type: $other"
          )
      }
    }
