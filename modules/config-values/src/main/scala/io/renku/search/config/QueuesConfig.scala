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

package io.renku.search.config

import cats.syntax.all.*
import ciris.{ConfigValue, Effect}
import io.renku.redis.client.QueueName

final case class QueuesConfig(
    projectCreated: QueueName,
    projectUpdated: QueueName,
    projectRemoved: QueueName,
    projectAuthorizationAdded: QueueName,
    projectAuthorizationUpdated: QueueName,
    projectAuthorizationRemoved: QueueName,
    userAdded: QueueName,
    userUpdated: QueueName,
    userRemoved: QueueName,
    groupAdded: QueueName,
    groupUpdated: QueueName,
    groupRemoved: QueueName,
    groupMemberAdded: QueueName,
    groupMemberUpdated: QueueName,
    groupMemberRemoved: QueueName,
    dataServiceAllEvents: QueueName
):
  lazy val all: Set[QueueName] = Set(
    projectCreated,
    projectUpdated,
    projectRemoved,
    projectAuthorizationAdded,
    projectAuthorizationUpdated,
    projectAuthorizationRemoved,
    userAdded,
    userUpdated,
    userRemoved,
    groupAdded,
    groupUpdated,
    groupRemoved,
    groupMemberAdded,
    groupMemberUpdated,
    groupMemberRemoved,
    dataServiceAllEvents
  )

object QueuesConfig:
  val config: ConfigValue[Effect, QueuesConfig] =
    (
      ConfigValues.eventQueue("projectCreated"),
      ConfigValues.eventQueue("projectUpdated"),
      ConfigValues.eventQueue("projectRemoved"),
      ConfigValues.eventQueue("projectAuthAdded"),
      ConfigValues.eventQueue("projectAuthUpdated"),
      ConfigValues.eventQueue("projectAuthRemoved"),
      ConfigValues.eventQueue("userAdded"),
      ConfigValues.eventQueue("userUpdated"),
      ConfigValues.eventQueue("userRemoved"),
      ConfigValues.eventQueue("groupAdded"),
      ConfigValues.eventQueue("groupUpdated"),
      ConfigValues.eventQueue("groupRemoved"),
      ConfigValues.eventQueue("groupMemberAdded"),
      ConfigValues.eventQueue("groupMemberUpdated"),
      ConfigValues.eventQueue("groupMemberRemoved"),
      ConfigValues.eventQueue("dataServiceAllEvents")
    ).mapN(QueuesConfig.apply)
