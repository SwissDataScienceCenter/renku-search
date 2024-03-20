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

package io.renku.search.perftests

import cats.Monad
import cats.effect.std.{Random, UUIDGen}
import cats.syntax.all.*
import io.renku.events.v1.{ProjectMemberRole, Visibility}
import io.renku.search.model.{Id, projects}

import java.time.Instant
import java.time.temporal.ChronoUnit
import java.time.temporal.ChronoUnit.DAYS

private trait ModelTypesGenerators[F[_]: Monad: Random: UUIDGen]:

  def generateId: F[Id] =
    UUIDGen.randomString[F].map(_.replace("-", "").toUpperCase).map(Id(_))
  def generateCreationDate: F[projects.CreationDate] =
    Random[F]
      .betweenLong(
        Instant.now().minus(5 * 365, DAYS).toEpochMilli,
        Instant.now().toEpochMilli
      )
      .map(Instant.ofEpochMilli)
      .map(projects.CreationDate.apply)
  def generateVisibility: F[projects.Visibility] =
    Random[F].shuffleList(projects.Visibility.values.toList).map(_.head)
  def generateV1Visibility: F[Visibility] =
    Random[F].shuffleList(Visibility.values().toList).map(_.head)
  def generateV1MemberRole: F[ProjectMemberRole] =
    Random[F].shuffleList(ProjectMemberRole.values().toList).map(_.head)
