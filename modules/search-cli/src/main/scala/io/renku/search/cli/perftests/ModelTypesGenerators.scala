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

package io.renku.search.cli.perftests

import cats.Monad
import cats.effect.IO
import cats.effect.std.{Random, UUIDGen}
import cats.syntax.all.*
import io.renku.search.events.*
import io.renku.search.model.{Id, projects}

import java.time.Instant
import java.time.temporal.ChronoUnit
import java.time.temporal.ChronoUnit.DAYS
import io.renku.search.model.MemberRole

private object ModelTypesGenerators:

  def forIO: IO[ModelTypesGenerators[IO]] =
    given UUIDGen[IO] = UUIDGen.fromSync[IO]
    Random.scalaUtilRandom[IO].map { u =>
      given Random[IO] = u
      new ModelTypesGenerators[IO] {}
    }

  def apply[F[_]](using ev: ModelTypesGenerators[F]): ModelTypesGenerators[F] = ev

private trait ModelTypesGenerators[F[_]: Monad: Random: UUIDGen]:

  def generateRequestId: F[RequestId] =
    UUIDGen.randomString[F].map(RequestId(_))

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

  def generateRole: F[MemberRole] =
    Random[F].shuffleList(MemberRole.values.toList).map(_.head)
