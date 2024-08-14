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

type SyncMessage = ProjectCreated | ProjectUpdated | ProjectRemoved | ProjectMemberAdded |
  ProjectMemberUpdated | ProjectMemberRemoved | UserAdded | UserUpdated | UserRemoved |
  GroupAdded | GroupUpdated | GroupRemoved | GroupMemberAdded | GroupMemberUpdated |
  GroupMemberRemoved

object SyncMessage:

  given EventMessageDecoder[SyncMessage] =
    EventMessageDecoder.instance { qm =>
      qm.header.msgType match
        case MsgType.ProjectCreated =>
          castUp(EventMessageDecoder[ProjectCreated].decode(qm))
        case MsgType.ProjectUpdated =>
          castUp(EventMessageDecoder[ProjectUpdated].decode(qm))
        case MsgType.ProjectRemoved =>
          castUp(EventMessageDecoder[ProjectRemoved].decode(qm))
        case _ =>
          ???
    }

  private inline def castUp[A <: SyncMessage](
      r: Either[DecodeFailure, EventMessage[A]]
  ): Either[DecodeFailure, EventMessage[SyncMessage]] =
    r.asInstanceOf[Either[DecodeFailure, EventMessage[SyncMessage]]]
