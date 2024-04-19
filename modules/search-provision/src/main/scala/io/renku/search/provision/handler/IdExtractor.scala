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

package io.renku.search.provision.handler

import io.renku.events.{v1, v2}
import io.renku.search.events.{ProjectCreated, ProjectUpdated}
import io.renku.search.model.Id

trait IdExtractor[A]:
  def getId(a: A): Id

object IdExtractor:
  def apply[A](using e: IdExtractor[A]): IdExtractor[A] = e

  def create[A](f: A => Id): IdExtractor[A] =
    (a: A) => f(a)

  def createStr[A](f: A => String): IdExtractor[A] =
    (a: A) => Id(f(a))

  given IdExtractor[ProjectCreated] = createStr(_.fold(_.id, _.id))
  given IdExtractor[ProjectUpdated] = createStr(_.fold(_.id, _.id))
  given IdExtractor[v1.ProjectRemoved] = createStr(_.id)
  given IdExtractor[v1.UserAdded] = createStr(_.id)
  given IdExtractor[v1.UserUpdated] = createStr(_.id)
  given IdExtractor[v1.UserRemoved] = createStr(_.id)
  given IdExtractor[v2.GroupAdded] = createStr(_.id)
  given IdExtractor[v2.GroupRemoved] = createStr(_.id)
  given IdExtractor[v1.ProjectAuthorizationAdded] = createStr(_.projectId)
  given IdExtractor[v1.ProjectAuthorizationUpdated] = createStr(_.projectId)
  given IdExtractor[v1.ProjectAuthorizationRemoved] = createStr(_.projectId)
