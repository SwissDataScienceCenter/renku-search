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

import java.time.Period

final case class DateTimeCalc(
    ref: PartialDateTime | RelativeDate,
    amount: Period,
    range: Boolean
):
  def asString: String =
    val period = amount.getDays.abs
    val sep =
      if (range) DateTimeCalc.range
      else if (amount.isNegative) DateTimeCalc.sub
      else DateTimeCalc.add
    ref match
      case d: PartialDateTime =>
        s"${d.asString}$sep${period}d"

      case d: RelativeDate =>
        s"${d.name}$sep${period}d"

object DateTimeCalc:
  private[query] val add: String = "+"
  private[query] val sub: String = "-"
  private[query] val range: String = "/"
