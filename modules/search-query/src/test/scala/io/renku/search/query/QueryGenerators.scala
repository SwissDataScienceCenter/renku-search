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
import io.renku.commons.Visibility
import org.scalacheck.Gen
import org.scalacheck.cats.implicits.*

import java.time.{Period, YearMonth, ZoneId, ZoneOffset}

object QueryGenerators:
  val utc: Gen[Option[ZoneId]] =
    Gen.frequency(2 -> Gen.const(Some(ZoneOffset.UTC)), 1 -> Gen.const(None))

  val partialTime: Gen[PartialDateTime.Time] =
    for {
      h <- Gen.choose(0, 23)
      m <- Gen.option(Gen.choose(0, 59))
      s <- Gen.option(Gen.choose(0, 59))
    } yield PartialDateTime.Time(h, m, s)

  val partialDate: Gen[PartialDateTime.Date] =
    for {
      y <- Gen.choose(1900, 2200)
      m <- Gen.option(Gen.choose(1, 12))
      maxDay = m.map(ym => YearMonth.of(y, ym).atEndOfMonth().getDayOfMonth)
      d <- maxDay.traverse(max => Gen.choose(1, max))
    } yield PartialDateTime.Date(y, m, d)

  val partialDateTime: Gen[PartialDateTime] =
    (partialDate, Gen.option(partialTime), utc).mapN(PartialDateTime.apply)

  val relativeDate: Gen[RelativeDate] =
    Gen.oneOf(RelativeDate.values.toSeq)

  val dateTimeCalc: Gen[DateTimeCalc] = {
    val ref: Gen[PartialDateTime | RelativeDate] =
      Gen.oneOf(partialDateTime, relativeDate)

    val period: Gen[Period] =
      Gen.oneOf((-8 to -1) ++ (1 to 8)).map(n => Period.ofDays(n))

    for {
      date <- ref
      amount <- period
      range <- Gen.oneOf(true, false)
    } yield DateTimeCalc(date, amount, range)
  }

  val dateTimeRef: Gen[DateTimeRef] =
    Gen.oneOf(
      partialDateTime.map(DateTimeRef.apply),
      relativeDate.map(DateTimeRef.apply),
      dateTimeCalc.map(DateTimeRef.apply)
    )

  val field: Gen[Field] =
    Gen.oneOf(Field.values.toSeq)

  // TODO move to commons
  val visibility: Gen[Visibility] =
    Gen.oneOf(Visibility.values.toSeq)

  // TODO move to commons
  def nelOfN[A](n: Int, gen: Gen[A]): Gen[NonEmptyList[A]] =
    for {
      e0 <- gen
      en <- Gen.listOfN(n - 1, gen)
    } yield NonEmptyList(e0, en)

  private val simpleString: Gen[String] = Gen.alphaNumStr
  private val quotedString: Gen[String] =
    Gen.alphaNumStr.map(s => s"\"$s\"")

  private val valueString: Gen[String] =
    Gen.oneOf(simpleString, quotedString)

  private val stringValues: Gen[NonEmptyList[String]] =
    Gen.choose(1, 4).flatMap(n => nelOfN(n, valueString))

  val projectIdTerm: Gen[FieldTerm] =
    stringValues.map(FieldTerm.ProjectIdIs(_))

  val nameTerm: Gen[FieldTerm] =
    stringValues.map(FieldTerm.NameIs(_))

  val slugTerm: Gen[FieldTerm] =
    stringValues.map(FieldTerm.SlugIs(_))

  val createdByTerm: Gen[FieldTerm] =
    stringValues.map(FieldTerm.CreatedByIs(_))

  val visibilityTerm: Gen[FieldTerm] =
    Gen
      .frequency(10 -> visibility.map(NonEmptyList.one), 1 -> nelOfN(2, visibility))
      .map(FieldTerm.VisibilityIs(_))

  private val comparison: Gen[Comparison] =
    Gen.oneOf(Comparison.values.toSeq)

  val createdTerm: Gen[FieldTerm] =
    for {
      cmp <- comparison
      len <- Gen.frequency(5 -> Gen.const(1), 1 -> Gen.choose(1, 3))
      pd <- nelOfN(len, dateTimeRef)
    } yield FieldTerm.Created(cmp, pd)

  val fieldTerm: Gen[FieldTerm] =
    Gen.oneOf(
      projectIdTerm,
      nameTerm,
      slugTerm,
      createdByTerm,
      visibilityTerm,
      createdTerm
    )

  val freeText: Gen[String] =
    Gen.choose(1, 5).flatMap { len =>
      Gen.listOfN(len, valueString).map(_.mkString(" "))
    }

  val segment: Gen[Query.Segment] =
    Gen.oneOf(
      fieldTerm.map(Query.Segment.Field.apply),
      freeText.map(Query.Segment.Text.apply)
    )

  val query: Gen[Query] =
    Gen
      .choose(0, 12)
      .flatMap(n => Gen.listOfN(n, segment))
      .map(Query.apply)
