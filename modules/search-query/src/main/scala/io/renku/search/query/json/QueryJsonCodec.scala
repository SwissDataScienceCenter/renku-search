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

package io.renku.search.query.json

import cats.data.NonEmptyList
import io.bullet.borer.compat.cats.*
import io.bullet.borer.{Decoder, Encoder, Reader, Writer}
import io.renku.search.model.EntityType
import io.renku.search.model.projects.Visibility
import io.renku.search.query.*
import io.renku.search.query.FieldTerm.*
import io.renku.search.query.Query.Segment

import scala.collection.mutable.ListBuffer

/** Use these json encoding to have it more convenient json than the derived version with
  * nested objects or discriminator field.
  *
  * {{{
  *  [
  *   {
  *     "id": ["p1", "p2"],
  *     "id": "p2",
  *     "name": "test",
  *     "_text": "some phrase",
  *     "creationDate": ["<", "2024-01-29T12:00"]
  *   }
  *   ]
  * }}}
  */
private[query] object QueryJsonCodec:
  private[this] val freeTextField = "_text"
  private[this] val sortTextField = "_sort"

  enum Name:
    case FieldName(v: Field)
    case SortName
    case TextName

  private given Decoder[Name] =
    new Decoder[Name]:
      def read(r: Reader): Name =
        if (r.tryReadString(freeTextField)) Name.TextName
        else if (r.tryReadString(sortTextField)) Name.SortName
        else Decoder[Field].map(Name.FieldName.apply).read(r)

  private def writeNelValue[T: Encoder](w: Writer, ts: NonEmptyList[T]): w.type =
    if (ts.tail.isEmpty) w.write(ts.head)
    else w.writeLinearSeq(ts.toList)

  private def writeFieldTermValue(w: Writer, term: FieldTerm): Writer =
    term match
      case FieldTerm.TypeIs(values) =>
        writeNelValue(w, values)

      case FieldTerm.IdIs(values) =>
        writeNelValue(w, values)

      case FieldTerm.NameIs(values) =>
        writeNelValue(w, values)

      case FieldTerm.SlugIs(values) =>
        writeNelValue(w, values)

      case FieldTerm.VisibilityIs(values) =>
        writeNelValue(w, values)

      case FieldTerm.CreatedByIs(values) =>
        writeNelValue(w, values)

      case FieldTerm.Created(cmp, date) =>
        Encoder.forTuple[(Comparison, List[DateTimeRef])].write(w, (cmp, date.toList))

  def encoder: Encoder[List[Segment]] =
    new Encoder[List[Segment]] {
      def write(w: Writer, values: List[Segment]): w.type =
        w.writeMapOpen(values.size)
        values.foreach {
          case Segment.Text(v) =>
            w.writeMapMember(freeTextField, v)
          case Segment.Field(v) =>
            w.write(v.field)
            writeFieldTermValue(w, v)
          case Segment.Sort(v) =>
            w.write(sortTextField)
            writeNelValue(w, v.fields)
        }
        w.writeMapClose()
    }

  private def readNel[T: Decoder](r: Reader): NonEmptyList[T] =
    if (r.hasString) NonEmptyList.of(r.read[T]())
    else Decoder[NonEmptyList[T]].read(r)

  private def readTermValue(r: Reader, name: Name): Segment =
    name match
      case Name.TextName =>
        Segment.Text(r.readString())

      case Name.FieldName(Field.Type) =>
        val values = readNel[EntityType](r)
        Segment.Field(TypeIs(values))

      case Name.FieldName(Field.Id) =>
        val values = readNel[String](r)
        Segment.Field(IdIs(values))

      case Name.FieldName(Field.Name) =>
        val values = readNel[String](r)
        Segment.Field(NameIs(values))

      case Name.FieldName(Field.Visibility) =>
        val values = readNel[Visibility](r)
        Segment.Field(VisibilityIs(values))

      case Name.FieldName(Field.Slug) =>
        val values = readNel[String](r)
        Segment.Field(SlugIs(values))

      case Name.FieldName(Field.CreatedBy) =>
        val values = readNel[String](r)
        Segment.Field(CreatedByIs(values))

      case Name.FieldName(Field.Created) =>
        val (cmp, date) =
          Decoder.forTuple[(Comparison, NonEmptyList[DateTimeRef])].read(r)
        Segment.Field(Created(cmp, date))

      case Name.SortName =>
        val values = readNel[Order.OrderedBy](r)
        Segment.Sort(Order(values))

  val decoder: Decoder[List[Segment]] =
    new Decoder[List[Segment]] {
      def read(r: Reader) = {
        val buffer = ListBuffer.newBuilder[Segment]
        if (r.hasMapHeader) {
          val size = r.readMapHeader()
          @annotation.tailrec
          def loop(remain: Long): Unit =
            if (remain > 0) {
              val key = r[Name]
              val value = readTermValue(r, key)
              buffer.addOne(value)
              loop(remain - 1)
            }
          loop(size)

        } else if (r.hasMapStart) {
          r.readMapStart()
          @annotation.tailrec
          def loop(): Unit =
            if (r.tryReadBreak()) ()
            else {
              val key = r[Name]
              val value = readTermValue(r, key)
              buffer.addOne(value)
              loop()
            }
          loop()
        } else r.unexpectedDataItem(expected = "Map")

        buffer.result().result()
      }
    }
