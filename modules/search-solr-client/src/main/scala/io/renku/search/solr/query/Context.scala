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

package io.renku.search.solr.query

import java.time.Instant
import cats.effect.{Clock, Sync}
import cats.syntax.all.*
import java.time.ZoneId
import cats.Applicative

trait Context[F[_]]:
  def currentTime: F[Instant]
  def zoneId: F[ZoneId]

object Context:
  def forSync[F[_]: Sync]: Context[F] =
    new Context[F]:
      def currentTime: F[Instant] = Clock[F].realTimeInstant
      def zoneId: F[ZoneId] = Sync[F].delay(ZoneId.systemDefault())

  def fixed[F[_]: Applicative](time: Instant, zone: ZoneId): Context[F] =
    new Context[F]:
      def currentTime = time.pure[F]
      def zoneId = zone.pure[F]

  def fixedZone[F[_]: Applicative: Clock](zone: ZoneId): Context[F] =
    new Context[F]:
      def currentTime = Clock[F].realTimeInstant
      def zoneId = zone.pure[F]
