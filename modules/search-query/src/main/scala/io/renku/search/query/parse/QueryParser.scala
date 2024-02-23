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

package io.renku.search.query.parse

import cats.data.NonEmptyList
import cats.parse.{Parser as P, Parser0 as P0}
import io.renku.search.model.projects.Visibility
import io.renku.search.query.*

private[query] object QueryParser {
  val basicString =
    P.charsWhile(c => c > ' ' && !c.isWhitespace && c != '"' && c != '\\' && c != ',')

  val qstring =
    basicString.backtrack.orElse(cats.parse.strings.Json.delimited.parser)

  val sp0: P0[Unit] = P.charsWhile0(_.isWhitespace).void
  val sp: P[Unit] = P.charsWhile(_.isWhitespace).void
  val comma: P[Unit] = P.char(',')
  val commaSep = comma.surroundedBy(sp0).backtrack

  def mkFieldNames(fs: Set[Field]) =
    fs.map(_.name) ++ fs.map(_.name.toLowerCase)

  def fieldNameFrom(candidates: Set[Field]) =
    P.stringIn(mkFieldNames(candidates)).map(Field.unsafeFromString)

  val comparison: P[Comparison] =
    P.stringIn(Comparison.values.map(_.asString)).map(Comparison.unsafeFromString)

  val is: P[Unit] = P.string(Comparison.Is.asString)
  val gt: P[Unit] = P.string(Comparison.GreaterThan.asString)
  val lt: P[Unit] = P.string(Comparison.LowerThan.asString)

  val visibility: P[Visibility] =
    P.stringIn(
      Visibility.values
        .map(_.name.toLowerCase)
        .toSet ++ Visibility.values.map(_.name).toSet
    ).map(Visibility.unsafeFromString)

  def nelOf[A](p: P[A], sep: P[Unit]) =
    (p ~ (sep *> p).rep0).map { case (h, t) => NonEmptyList(h, t) }

  val values: P[NonEmptyList[String]] =
    nelOf(qstring, commaSep)

  val visibilities: P[NonEmptyList[Visibility]] =
    nelOf(visibility, commaSep)

  val termIs: P[FieldTerm] = {
    val field = fieldNameFrom(Field.values.toSet - Field.Created - Field.Visibility)
    ((field <* is) ~ values).map { case (f, v) =>
      f match
        case Field.Name       => FieldTerm.NameIs(v)
        case Field.ProjectId  => FieldTerm.ProjectIdIs(v)
        case Field.Slug       => FieldTerm.SlugIs(v)
        case Field.CreatedBy  => FieldTerm.CreatedByIs(v)
        case Field.Visibility => sys.error("visibility not allowed")
        case Field.Created    => sys.error("created not allowed")
    }
  }

  val visibilityIs: P[FieldTerm] = {
    val field = fieldNameFrom(Set(Field.Visibility))
    ((field ~ is).void *> visibilities).map(v => FieldTerm.VisibilityIs(v))
  }

  val created: P[FieldTerm] = {
    val field = fieldNameFrom(Set(Field.Created))
    ((field *> comparison) ~ nelOf(DateTimeParser.dateTimeRef, commaSep)).map {
      case (cmp, pdate) =>
        FieldTerm.Created(cmp, pdate)
    }
  }

  val fieldTerm: P[FieldTerm] = termIs | visibilityIs | created

  val freeText: P[String] =
    P.charsWhile(c => !c.isWhitespace)

  val segment: P[Query.Segment] =
    fieldTerm.map(Query.Segment.Field.apply) |
      freeText.map(Query.Segment.Text.apply)

  val query: P[Query] =
    segment.repSep(min = 1, sp).map(s => Query(s.toList))
}