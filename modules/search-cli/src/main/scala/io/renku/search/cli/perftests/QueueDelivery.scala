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

package io.renku.search.cli.perftests

import cats.Show

import io.renku.avro.codec.AvroEncoder
import io.renku.redis.client.QueueName
import io.renku.search.events.*

private trait QueueDelivery:
  type P <: RenkuEventPayload
  def queue: QueueName
  def encoder: AvroEncoder[P]
  def message: EventMessage[P]

private object QueueDelivery:
  def apply[A <: RenkuEventPayload: AvroEncoder](
      q: QueueName,
      msg: EventMessage[A]
  ): QueueDelivery =
    new QueueDelivery:
      override type P = A
      override val encoder: AvroEncoder[P] = summon[AvroEncoder[P]]
      override val queue: QueueName = q
      override val message: EventMessage[P] = msg

  given Show[QueueDelivery] = Show.show { delivery =>
    s"""queue: '${delivery.queue}' msg: ${delivery.message.toString}"""
  }
