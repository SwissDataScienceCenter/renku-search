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

trait EventMessageDecoder[T]:
  def decode(qm: QueueMessage): Either[DecodeFailure, EventMessage[T]]

object EventMessageDecoder:
  def apply[T](using emd: EventMessageDecoder[T]): EventMessageDecoder[T] = emd
  def instance[T](
      f: QueueMessage => Either[DecodeFailure, EventMessage[T]]
  ): EventMessageDecoder[T] =
    (qm: QueueMessage) => f(qm)
