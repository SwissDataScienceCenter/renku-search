package io.renku.avro.codec.decoders

import io.renku.avro.codec.{AvroCodecException, AvroDecoder}
import org.apache.avro.Schema
import org.apache.avro.generic.IndexedRecord

import scala.compiletime.*
import scala.deriving.*
import scala.jdk.CollectionConverters.*

trait RecordDecoders:
  inline given [T <: Product](using A: Mirror.ProductOf[T]): AvroDecoder[T] =
    RecordDecoders.Macros.deriveViaMirror[T]

object RecordDecoders:

  final inline def derived[A <: Product](using
      inline A: Mirror.ProductOf[A]
  ): AvroDecoder[A] =
    RecordDecoders.Macros.deriveViaMirror[A]

  private object Macros {

    inline given deriveViaMirror[A <: Product](using
        m: Mirror.ProductOf[A]
    ): AvroDecoder[A] =
      val decoders = summonAll[m.MirroredElemTypes]
      AvroDecoder { (schema, a) =>
        require(schema.getType == Schema.Type.RECORD)
        a match
          case r: IndexedRecord =>
            val len = decoders.length
            var i = len - 1
            var result: Tuple = EmptyTuple
            while (i >= 0) {
              val fieldSchema = schema.getFields.get(i).schema()
              val decoded = decoders(i).decode(fieldSchema).apply(r.get(i))
              result = decoded *: result
              i = i - 1
            }
            m.fromTuple(result.asInstanceOf[m.MirroredElemTypes])

          case _ =>
            throw AvroCodecException.decode(
              s"This record decoder can only handle IndexedRecords, was ${a.getClass}"
            )
      }

    inline def summonAll[T <: Tuple]: List[AvroDecoder[?]] =
      inline erasedValue[T] match
        case _: EmptyTuple => Nil
        case _: (t *: ts)  => summonInline[AvroDecoder[t]] :: summonAll[ts]
  }
