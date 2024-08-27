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

package io.renku.solr.client

import scala.concurrent.duration.*

import cats.effect.*
import cats.effect.std.Random
import cats.syntax.all.*

enum UpsertResponse:
  case Success(header: ResponseHeader)
  case VersionConflict

  lazy val isSuccess: Boolean = this match
    case Success(_)      => true
    case VersionConflict => false

  lazy val isFailure: Boolean = !isSuccess

object UpsertResponse:

  def retryOnConflict[F[_]: Async](
      retries: Int,
      maxWait: Duration = 100.millis
  )(fa: F[UpsertResponse]): F[UpsertResponse] =
    val logger = scribe.cats.effect[F]
    fa.flatMap {
      case r @ UpsertResponse.Success(_) => r.pure[F]
      case r @ UpsertResponse.VersionConflict =>
        if (retries <= 0) logger.warn("Retries on version conflict exceeded").as(r)
        else
          for
            _ <- logger.info(
              s"Version conflict on SOLR update, retries left $retries"
            )
            rand <- Random.scalaUtilRandom[F]
            waitMillis <- rand.betweenLong(5, math.max(maxWait.toMillis, 10))
            _ <- logger.debug(s"Wait ${waitMillis}ms before next try")
            _ <- Async[F].sleep(waitMillis.millis)
            res <- retryOnConflict(retries - 1, maxWait)(fa)
          yield res
    }

  object syntax {
    extension [F[_]](self: F[UpsertResponse])
      def retryOnConflict(
          retries: Int,
          maxWait: Duration = 100.millis
      )(using Async[F]): F[UpsertResponse] =
        UpsertResponse.retryOnConflict(retries, maxWait)(self)
  }
