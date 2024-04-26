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

import io.renku.search.events.EventMessage
import io.renku.search.model.Id

final case class EntityOrPartialMessage[A: IdExtractor](
    message: EventMessage[A],
    documents: Map[Id, EntityOrPartial]
):
  def merge(
      ifEmpty: A => Option[EntityOrPartial],
      ifMerge: (A, EntityOrPartial) => Option[EntityOrPartial]
  ): EventMessage[EntityOrPartial] =
    EventMessage(
      message.id,
      message.header,
      message.payloadSchema,
      message.payload.flatMap { a =>
        documents
          .get(IdExtractor[A].getId(a))
          .map(doc => ifMerge(a, doc))
          .getOrElse(ifEmpty(a))
      }
    )

  lazy val asMessage: EventMessage[EntityOrPartial] =
    EventMessage(
      message.id,
      message.header,
      message.payloadSchema,
      documents.values.toSeq
    )
