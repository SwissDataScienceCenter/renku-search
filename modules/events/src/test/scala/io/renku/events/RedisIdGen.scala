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

package io.renku.events

import java.util.concurrent.atomic.AtomicLong

import cats.effect.*

import io.renku.search.events.MessageId

// Just for testing, generate redis compatible ids
object RedisIdGen:

  private val millis = new AtomicLong(System.currentTimeMillis())
  private val counter = new AtomicLong(0)

  def unsafeNextId: MessageId =
    val c = counter.getAndIncrement()
    val ms = System.currentTimeMillis()
    val curMs = millis.get
    if (millis.get == ms) {
      MessageId(s"${ms}-${counter.getAndIncrement()}")
    } else {
      if (millis.compareAndSet(curMs, ms)) {
        counter.set(0)
        MessageId(s"${ms}-$c")
      } else
        unsafeNextId
    }

  def nextId: IO[MessageId] =
    IO(unsafeNextId)
