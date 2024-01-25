package io.renku.solr.client

import io.bullet.borer.{Encoder, Writer}
import scala.deriving.*
import scala.compiletime.*

object EncoderSupport {

  inline def deriveWithDiscriminator[A <: Product](using
      Mirror.ProductOf[A]
  ): Encoder[A] =
    Macros.createEncoder[String, A]("_type")

  private object Macros {

    final inline def createEncoder[K: Encoder, T <: Product](discriminatorName: K)(using
        m: Mirror.ProductOf[T]
    ): Encoder[T] =
      val names = summonLabels[m.MirroredElemLabels]
      val encoders = summonEncoder[m.MirroredElemTypes]

      new Encoder[T]:
        def write(w: Writer, value: T): Writer =
          val kind = value.asInstanceOf[Product].productPrefix
          val values = value.asInstanceOf[Product].productIterator.toList
          w.writeMapOpen(names.size + 1)
          w.writeMapMember(discriminatorName, kind)
          names.zip(values).zip(encoders).foreach { case ((k, v), e) =>
            w.writeMapMember(k, v)(Encoder[String], e.asInstanceOf[Encoder[Any]])
          }
          w.writeMapClose()

    inline def summonEncoder[A <: Tuple]: List[Encoder[_]] =
      inline erasedValue[A] match
        case _: EmptyTuple => Nil
        case _: (t *: ts)  => summonInline[Encoder[t]] :: summonEncoder[ts]

    inline def summonLabels[A <: Tuple]: List[String] =
      inline erasedValue[A] match
        case _: EmptyTuple => Nil
        case _: (t *: ts)  => constValue[t].toString :: summonLabels[ts]
  }

}
