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

package io.renku.search.provision.variant

import io.renku.search.model.Id
import io.renku.events.v1.*

trait IdExtractor[A]:
  def getId(a: A): Id

object IdExtractor:
  def apply[A](using e: IdExtractor[A]): IdExtractor[A] = e

  def create[A](f: A => Id): IdExtractor[A] =
    (a: A) => f(a)

  def createStr[A](f: A => String): IdExtractor[A] =
    (a: A) => Id(f(a))

  given IdExtractor[ProjectUpdated] = createStr(_.id)
  given IdExtractor[ProjectRemoved] = createStr(_.id)
  given IdExtractor[UserUpdated] = createStr(_.id)
  given IdExtractor[UserRemoved] = createStr(_.id)
  given IdExtractor[ProjectAuthorizationAdded] = createStr(_.projectId)
  given IdExtractor[ProjectAuthorizationUpdated] = createStr(_.projectId)
  given IdExtractor[ProjectAuthorizationRemoved] = createStr(_.projectId)
