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

import cats.MonadThrow
import fs2.Stream
import io.renku.events.v1.UserAdded

private trait UserAddedGenerator[F[_]]:
  def generateUserAdded: Stream[F, UserAdded]

private object UserAddedGenerator:
  def apply[F[_]: MonadThrow](
      randomDataFetcher: RandomDataFetcher[F]
  ): UserAddedGenerator[F] =
    new UserAddedGeneratorImpl[F](randomDataFetcher)

private class UserAddedGeneratorImpl[F[_]: MonadThrow](
    randomDataFetcher: RandomDataFetcher[F]
) extends UserAddedGenerator[F]
    with ModelTypesGenerators:

  override def generateUserAdded: Stream[F, UserAdded] =
    randomDataFetcher.findNames.map { case (first, last) =>
      UserAdded(generateId.value, Some(first.value), Some(last.value), None)
    }
