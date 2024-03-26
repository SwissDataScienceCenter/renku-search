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
import io.renku.queue.client.MessageHeader
import io.renku.redis.client.QueueName

private trait QueueDelivery:
  type P
  val queue: QueueName
  val header: MessageHeader
  val payload: P
  val encoder: AvroEncoder[P]

private object QueueDelivery:
  def apply[O: AvroEncoder](q: QueueName, h: MessageHeader, p: O): QueueDelivery =
    new QueueDelivery:
      override type P = O
      override val queue: QueueName = q
      override val header: MessageHeader = h
      override val payload: O = p
      override val encoder: AvroEncoder[O] = AvroEncoder[O]

  given Show[QueueDelivery] = Show.show { delivery =>
    s"""'${delivery.queue}' queue: '${delivery.payload}'"""
  }
