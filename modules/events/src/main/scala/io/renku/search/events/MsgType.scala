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

package io.renku.search.events

/** This represents expected values of the `type` property in the message header. */
enum MsgType(val name: String):
  case ProjectCreated extends MsgType("project.created")
  case ProjectUpdated extends MsgType("project.updated")
  case ProjectRemoved extends MsgType("project.removed")
  case ProjectMemberAdded extends MsgType("projectAuth.added")
  case ProjectMemberUpdated extends MsgType("projectAuth.updated")
  case ProjectMemberRemoved extends MsgType("projectAuth.removed")
  case UserAdded extends MsgType("user.added")
  case UserUpdated extends MsgType("user.updated")
  case UserRemoved extends MsgType("user.removed")
  case GroupAdded extends MsgType("group.added")
  case GroupUpdated extends MsgType("group.updated")
  case GroupRemoved extends MsgType("group.removed")
  case GroupMemberAdded extends MsgType("memberGroup.added")
  case GroupMemberUpdated extends MsgType("memberGroup.updated")
  case GroupMemberRemoved extends MsgType("memberGroup.removed")
  case ReprovisioningStarted extends MsgType("reprovisioning.started")
  case ReprovisioningFinished extends MsgType("reprovisioning.finished")

object MsgType:
  def fromString(s: String): Either[String, MsgType] =
    MsgType.values
      .find(e => e.name.equalsIgnoreCase(s) || e.productPrefix.equalsIgnoreCase(s))
      .toRight(s"Invalid msg type: $s")
