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
import java.time.ZoneId

import cats.Applicative
import cats.effect.{Clock, Sync}
import cats.syntax.all.*

import io.renku.search.solr.SearchRole

trait Context[F[_]]:
  def currentTime: F[Instant]
  def zoneId: F[ZoneId]
  def role: SearchRole

object Context:
  def forSync[F[_]: Sync](searchRole: SearchRole): Context[F] =
    new Context[F]:
      val currentTime: F[Instant] = Clock[F].realTimeInstant
      val zoneId: F[ZoneId] = Sync[F].delay(ZoneId.systemDefault())
      val role = searchRole

  def fixed[F[_]: Applicative](
      time: Instant,
      zone: ZoneId,
      searchRole: SearchRole
  ): Context[F] =
    new Context[F]:
      val currentTime = time.pure[F]
      val zoneId = zone.pure[F]
      val role = searchRole

  def fixedZone[F[_]: Applicative: Clock](
      zone: ZoneId,
      searchRole: SearchRole
  ): Context[F] =
    new Context[F]:
      val currentTime = Clock[F].realTimeInstant
      val zoneId = zone.pure[F]
      val role = searchRole
