/*
 * Copyright 2024 Swiss Data Science Center (SDSC)
 * A partnership between École Polytechnique Fédérale de Lausanne (EPFL) and
 * Eidgenössische Technische Hochschule Zürich (ETHZ).
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.renku.json

import scala.compiletime.*
import scala.deriving.*

import cats.kernel.Monoid
import cats.syntax.foldable.*

import io.bullet.borer.{Encoder, Writer}

/** Provides encoder derived from product and sum types in the following way:
  *
  *   - for product types, it allow to add more fields to the json object using the
  *     `additionalFields` argument
  *   - for sum types, it simply selects the encoder in scope for the specific branch;
  *     i.e. it **does not** add any discriminator - they would need to be included into
  *     each branch already
  *
  * When writing data to solr, it is sometimes useful to amend it with additional
  * properties that don't need to be decoded, but can be used when querying. Since
  * decoding is much more difficult, this is left to borers `AdtEncodingStrategy.flat`. It
  * is then required to add the correct discriminator to each branch of the ADT manually,
  * together with eventual more properties. Then for the sealed parent, use
  * `EncoderSupport.derive[ADT]` to select an encoder for each branch.
  */
object EncoderSupport {
  trait AdditionalFields[A, V]:
    def make(a: A): Map[String, V]
    def ++(other: AdditionalFields[A, V]): AdditionalFields[A, V] =
      (a: A) => make(a) ++ other.make(a)

  object AdditionalFields:
    def none[A, V]: AdditionalFields[A, V] = create(_ => Map.empty)

    def create[A, V](f: A => Map[String, V]): AdditionalFields[A, V] =
      (a: A) => f(a)

    def of[A, V](fields: (String, A => V)*): AdditionalFields[A, V] =
      create(a => fields.toMap.view.mapValues(_.apply(a)).toMap)

    def const[A, V](fields: (String, V)*): AdditionalFields[A, V] =
      create(_ => fields.toMap)

    def productPrefix[A <: Product](fieldName: String): AdditionalFields[A, String] =
      create(a => Map(fieldName -> a.productPrefix))

    given [A, V]: Monoid[AdditionalFields[A, V]] =
      Monoid.instance(none[A, V], _ ++ _)

  inline def derive[A](using Mirror.Of[A]): Encoder[A] =
    Macros.createEncoder[String, String, A](AdditionalFields.none[A, String])

  inline def deriveWith[A, V: Encoder](
      additionalFields: AdditionalFields[A, V]*
  )(using Mirror.Of[A]): Encoder[A] =
    Macros.createEncoder[String, V, A](additionalFields.combineAll)

  inline def deriveWithDiscriminator[A <: Product](discriminatorField: String)(using
      Mirror.Of[A]
  ): Encoder[A] =
    val adds = AdditionalFields.of[A, String](discriminatorField -> (_.productPrefix))
    deriveWith[A, String](adds)

  inline def deriveWithAdditional[A <: Product, V: Encoder](field: (String, V)*)(using
      Mirror.Of[A]
  ): Encoder[A] =
    val adds = AdditionalFields.const[A, V](field*)
    Macros.createEncoder[String, V, A](adds)

  /** Derives an encoder that writes all members of the target type as map members. It
    * assumes an already open map!
    */
  inline def deriveProductMemberEncoder[A <: Product](using
      Mirror.ProductOf[A]
  ): Encoder[A] =
    Macros.membersEncoder[A]

  private object Macros {
    final inline def membersEncoder[T](using
        m: Mirror.ProductOf[T]
    ): Encoder[T] =
      new Encoder[T] {
        def write(w: Writer, value: T): Writer =
          val encoders = summonEncoder[m.MirroredElemTypes]
          val names = LabelsMacro.findLabels[T].toList
          val values = value.asInstanceOf[Product].productIterator.toList
          names.zip(values).zip(encoders).foreach { case ((k, v), e) =>
            w.writeMapMember(k, v)(using Encoder[String], e.asInstanceOf[Encoder[Any]])
          }
          w
      }

    final inline def createEncoder[K: Encoder, V: Encoder, T](
        additionalFields: AdditionalFields[T, V]
    )(using m: Mirror.Of[T]): Encoder[T] =
      val encoders = summonEncoder[m.MirroredElemTypes]
      val names = LabelsMacro.findLabels[T].toList
      inline m match
        case s: Mirror.SumOf[T] =>
          createSumEncoder[K, V, T](s, encoders)

        case p: Mirror.ProductOf[T] =>
          createProductEncoder[K, V, T](encoders, names, additionalFields)

    private def createProductEncoder[K: Encoder, V: Encoder, T](
        encoders: List[Encoder[?]],
        names: List[String],
        additionalFields: AdditionalFields[T, V]
    ): Encoder[T] =
      new Encoder[T]:
        def write(w: Writer, value: T): Writer =
          val additionalProps = additionalFields.make(value)
          val values = value.asInstanceOf[Product].productIterator.toList
          w.writeMapOpen(names.size + additionalProps.size)
          additionalProps.foreach { case (k, v) => w.writeMapMember(k, v) }
          names.zip(values).zip(encoders).foreach { case ((k, v), e) =>
            w.writeMapMember(k, v)(using Encoder[String], e.asInstanceOf[Encoder[Any]])
          }
          w.writeMapClose()

    private def createSumEncoder[K: Encoder, V: Encoder, T](
        s: Mirror.SumOf[T],
        encoders: => List[Encoder[?]]
    ): Encoder[T] =
      new Encoder[T] {
        def write(w: Writer, value: T): Writer =
          val ord = s.ordinal(value)
          encoders(ord).asInstanceOf[Encoder[Any]].write(w, value)
      }

    inline def summonEncoder[A <: Tuple]: List[Encoder[?]] =
      inline erasedValue[A] match
        case _: EmptyTuple => Nil
        case _: (t *: ts)  => summonInline[Encoder[t]] :: summonEncoder[ts]
  }
}
