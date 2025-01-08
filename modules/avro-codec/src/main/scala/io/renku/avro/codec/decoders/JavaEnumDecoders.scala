package io.renku.avro.codec.decoders

import io.renku.avro.codec.AvroDecoder
import org.apache.avro.generic.GenericEnumSymbol

import scala.reflect.ClassTag

trait JavaEnumDecoders {

  given [E <: Enum[E]](using ctag: ClassTag[E]): AvroDecoder[E] = AvroDecoder.basic {
    case e: Enum[?] => e.asInstanceOf[E]
    case e: GenericEnumSymbol[?] =>
      Enum.valueOf[E](ctag.runtimeClass.asInstanceOf[Class[E]], e.toString)
  }
}
