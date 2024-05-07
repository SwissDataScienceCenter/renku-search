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

import java.time.*
import java.time.temporal.ChronoUnit

import cats.syntax.all.*

import io.bullet.borer.{Decoder, Encoder}
import io.renku.search.query.PartialDateTime.prefixT
import io.renku.search.query.parse.DateTimeParser

final case class PartialDateTime(
    date: PartialDateTime.Date,
    time: Option[PartialDateTime.Time] = None,
    zoneId: Option[ZoneId] = None
):
  def asString: String =
    s"${date.asString}${prefixT(time.map(_.asString))}${zoneId.map(_.getId).orEmpty}"

  def isExact: Boolean =
    date.isExact && time.exists(_.isExact)

  def zonedMax(defaultZone: ZoneId): ZonedDateTime =
    ZonedDateTime.of(
      date.max,
      time.map(_.max).getOrElse(PartialDateTime.maxTime),
      zoneId.getOrElse(defaultZone)
    )

  def instantMax(defaultZone: ZoneId): Instant =
    zonedMax(defaultZone).toInstant.truncatedTo(ChronoUnit.SECONDS)

  def zonedMin(defaultZone: ZoneId): ZonedDateTime =
    ZonedDateTime.of(
      date.min,
      time.map(_.min).getOrElse(LocalTime.MIDNIGHT),
      zoneId.getOrElse(defaultZone)
    )

  def instantMin(defaultZone: ZoneId): Instant =
    zonedMin(defaultZone).toInstant.truncatedTo(ChronoUnit.SECONDS)

object PartialDateTime:
  private val maxTime: LocalTime = LocalTime.MIDNIGHT.minusSeconds(1)
  private def dash(n: Option[Int]): String =
    n.map(i => s"-$i").getOrElse("")
  private def colon(n: Option[Int]): String =
    n.map(i => s":$i").getOrElse("")
  private def prefixT(str: Option[String]): String =
    str.map(s => s"T$s").getOrElse("")

  given Encoder[PartialDateTime] = Encoder.forString.contramap(_.asString)
  given Decoder[PartialDateTime] = Decoder.forString.mapEither { str =>
    DateTimeParser.partialDateTime.parseAll(str.trim).leftMap(_.show)
  }

  def fromInstant(dt: Instant): PartialDateTime =
    fromString(dt.truncatedTo(ChronoUnit.SECONDS).toString)
      .fold(err => sys.error(s"Parsing valid instant failed\n$err"), identity)

  def fromString(str: String): Either[String, PartialDateTime] =
    DateTimeParser.partialDateTime
      .parseAll(str)
      .leftMap(_.show)

  def unsafeFromString(str: String): PartialDateTime =
    fromString(str).fold(sys.error, identity)

  final case class Date(
      year: Int,
      month: Option[Int] = None,
      dayOfMonth: Option[Int] = None
  ):
    def asString: String = s"$year${dash(month)}${dash(dayOfMonth)}"
    def isExact: Boolean = month.isDefined && dayOfMonth.isDefined
    def max: LocalDate = {
      val m = month.getOrElse(12)
      val md = YearMonth.of(year, m).atEndOfMonth().getDayOfMonth
      LocalDate.of(year, m, dayOfMonth.getOrElse(md))
    }
    def min: LocalDate =
      LocalDate.of(year, month.getOrElse(1), dayOfMonth.getOrElse(1))

  final case class Time(
      hour: Int,
      minute: Option[Int] = None,
      sec: Option[Int] = None
  ):
    def asString: String = s"$hour${colon(minute)}${colon(sec)}"
    def isExact: Boolean = minute.isDefined && sec.isDefined
    def max: LocalTime =
      LocalTime.of(hour, minute.getOrElse(59), sec.getOrElse(59))

    def min: LocalTime =
      LocalTime.of(hour, minute.getOrElse(0), sec.getOrElse(0))
