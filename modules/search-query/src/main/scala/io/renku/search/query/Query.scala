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
import io.bullet.borer.{Decoder, Encoder}
import io.renku.commons.Visibility
import io.renku.search.query.Query.Segment

import java.time.Instant
import java.time.temporal.ChronoUnit

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

  enum Segment:
    case Field(value: FieldTerm)
    case Text(value: String)

  object Segment:
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

    def creationDateIs(date: Instant): Segment =
      Segment.Field(
        FieldTerm.Created(Comparison.Is, date.truncatedTo(ChronoUnit.SECONDS))
      )

    def creationDateGreater(date: Instant): Segment =
      Segment.Field(
        FieldTerm.Created(Comparison.GreaterThan, date.truncatedTo(ChronoUnit.SECONDS))
      )

    def creationDateLower(date: Instant): Segment =
      Segment.Field(
        FieldTerm.Created(Comparison.LowerThan, date.truncatedTo(ChronoUnit.SECONDS))
      )

  val empty: Query = Query(Nil)

  def apply(s: Segment*): Query = Query(s.toList)
