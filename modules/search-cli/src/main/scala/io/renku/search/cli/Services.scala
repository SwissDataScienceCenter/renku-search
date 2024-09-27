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

package io.renku.search.cli

import cats.effect.*

import io.renku.queue.client.QueueClient
import io.renku.redis.client.ClientId
import io.renku.search.config.ConfigValues
import io.renku.search.config.QueuesConfig
import io.renku.search.events.*

object Services:
  private val millis = System.currentTimeMillis()
  private val clientId: ClientId = ClientId("search-provision")
  private val counter = Ref.unsafe[IO, Long](0)

  val configValues = ConfigValues()
  val queueConfig = QueuesConfig.config(configValues)

  def queueClient: Resource[IO, QueueClient[IO]] =
    val redisCfg = ConfigValues().redisConfig.load[IO]
    Resource.eval(redisCfg).flatMap(QueueClient.make[IO](_, clientId))

  private def makeRequestId: IO[RequestId] =
    counter.updateAndGet(_ + 1).map(n => RequestId(s"req_${millis}_$n"))

  def createMessage[A <: RenkuEventPayload](payload: A): IO[EventMessage[A]] =
    for
      reqId <- makeRequestId
      src = MessageSource("search-cli")
      id = MessageId("*")
      msg <- EventMessage.create[IO, A](id, src, DataContentType.Binary, reqId, payload)
    yield msg
