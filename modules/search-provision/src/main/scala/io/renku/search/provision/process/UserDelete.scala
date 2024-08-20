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

package io.renku.search.provision.process

import cats.effect.*
import cats.syntax.all.*

import io.renku.search.events.EventMessage
import io.renku.search.events.UserRemoved
import io.renku.search.provision.handler.*

/** Delete a user.
  *
  * Deleting a user requires to update all affected entities to remove this user from
  * their members.
  */
final private[provision] class UserDelete[F[_]: Async](ps: PipelineSteps[F]):
  private val logger = scribe.cats.effect[F]

  /** Delete all users the payload. */
  def process(
      msg: EventMessage[UserRemoved]
  ): F[DeleteFromSolr.DeleteResult[UserRemoved]] =
    for
      _ <- logger.info(s"Deleting users for message: $msg")
      delRes <- ps.deleteFromSolr.deleteDocuments(msg)
      _ <- delRes match
        case DeleteFromSolr.DeleteResult.Success(_) =>
          ps.userUtils.removeMember(msg).compile.drain
        case _ =>
          ().pure[F]
    yield delRes
