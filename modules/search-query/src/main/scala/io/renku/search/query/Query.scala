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

package io.renku.search.query

import cats.data.NonEmptyList
import cats.syntax.all.*
import io.bullet.borer.{Decoder, Encoder}
import io.renku.commons.Visibility
import io.renku.search.query.FieldTerm.Created
import io.renku.search.query.Query.Segment
import io.renku.search.query.json.QueryJsonCodec
import io.renku.search.query.parse.{QueryParser, QueryUtil}

final case class Query(
    segments: List[Query.Segment]
):
  def asString: String =
    segments
      .map {
        case Query.Segment.Field(v) => v.asString
        case Query.Segment.Text(v)  => v
      }
      .mkString(" ")

  def isEmpty: Boolean = segments.isEmpty

object Query:
  given Encoder[Query] = QueryJsonCodec.encoder.contramap(_.segments)
  given Decoder[Query] = QueryJsonCodec.decoder.map(Query.apply)

  def parse(str: String): Either[String, Query] =
    val trimmed = str.trim
    if (trimmed.isEmpty) Right(empty)
    else
      QueryParser.query
        .parseAll(trimmed)
        .leftMap(_.show)
        .map(QueryUtil.collapse)

  enum Segment:
    case Field(value: FieldTerm)
    case Text(value: String)

  object Segment:
    extension (self: Segment.Text)
      def ++(other: Segment.Text): Segment.Text =
        if (other.value.isEmpty) self
        else if (self.value.isEmpty) other
        else Segment.Text(s"${self.value} ${other.value}")

    def text(phrase: String): Segment =
      Segment.Text(phrase)

    def projectIdIs(value: String, more: String*): Segment =
      Segment.Field(FieldTerm.ProjectIdIs(NonEmptyList(value, more.toList)))

    def nameIs(value: String, more: String*): Segment =
      Segment.Field(FieldTerm.NameIs(NonEmptyList(value, more.toList)))

    def slugIs(value: String, more: String*): Segment =
      Segment.Field(FieldTerm.SlugIs(NonEmptyList(value, more.toList)))

    def visibilityIs(value: Visibility, more: Visibility*): Segment =
      Segment.Field(FieldTerm.VisibilityIs(NonEmptyList(value, more.toList)))

    def creationDateIs(date: DateTimeRef, dates: DateTimeRef*): Segment =
      Segment.Field(Created(Comparison.Is, NonEmptyList(date, dates.toList)))

    def creationDateLt(date: DateTimeRef, dates: DateTimeRef*): Segment =
      Segment.Field(Created(Comparison.LowerThan, NonEmptyList(date, dates.toList)))

    def creationDateGt(date: DateTimeRef, dates: DateTimeRef*): Segment =
      Segment.Field(Created(Comparison.GreaterThan, NonEmptyList(date, dates.toList)))

    def creationDateIs(date: PartialDateTime, dates: PartialDateTime*): Segment =
      creationDateIs(DateTimeRef(date), dates.map(DateTimeRef.apply): _*)

    def creationDateGt(date: PartialDateTime, dates: PartialDateTime*): Segment =
      creationDateGt(DateTimeRef(date), dates.map(DateTimeRef.apply): _*)

    def creationDateLt(date: PartialDateTime, dates: PartialDateTime*): Segment =
      creationDateLt(DateTimeRef(date), dates.map(DateTimeRef.apply): _*)

  val empty: Query = Query(Nil)

  def apply(s: Segment*): Query = Query(s.toList)
