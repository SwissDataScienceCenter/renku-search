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

import java.time.Instant
import java.time.ZoneId

import cats.syntax.all.*

import io.bullet.borer.{Decoder, Encoder}
import io.renku.search.query.parse.DateTimeParser

enum DateTimeRef:
  case Literal(ref: PartialDateTime)
  case Relative(ref: RelativeDate)
  case Calc(ref: DateTimeCalc)

  val asString: String = this match
    case Literal(ref)  => ref.asString
    case Relative(ref) => ref.name
    case Calc(ref)     => ref.asString

  /** Resolves the date-time reference to a concrete instant using the given reference
    * date. It either returns a single instant or a time range.
    */
  def resolve(ref: Instant, zoneId: ZoneId): (Instant, Option[Instant]) = this match
    case Relative(RelativeDate.Today) => (ref, None)
    case Relative(RelativeDate.Yesterday) =>
      (ref.atZone(zoneId).minusDays(1).toInstant, None)
    case Literal(pdate) =>
      val min = pdate.instantMin(zoneId)
      val max = pdate.instantMax(zoneId)
      (min, Some(max).filter(_ != min))
    case Calc(cdate) =>
      val ts = cdate.ref match
        case pd: PartialDateTime =>
          pd.instantMin(zoneId).atZone(zoneId)

        case rd: RelativeDate =>
          Relative(rd).resolve(ref, zoneId)._1.atZone(zoneId)

      if (cdate.range)
        (ts.minus(cdate.amount).toInstant, Some(ts.plus(cdate.amount).toInstant))
      else (ts.plus(cdate.amount).toInstant, None)

object DateTimeRef:
  given Encoder[DateTimeRef] = Encoder.forString.contramap(_.asString)
  given Decoder[DateTimeRef] = Decoder.forString.mapEither { str =>
    DateTimeParser.dateTimeRef.parseAll(str).leftMap(_.show)
  }

  def apply(ref: PartialDateTime): DateTimeRef = Literal(ref)
  def apply(ref: RelativeDate): DateTimeRef = Relative(ref)
  def apply(ref: DateTimeCalc): DateTimeRef = Calc(ref)
