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

package io.renku.search

import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

import scala.util.Try

import cats.effect.*

import munit.*

trait Threading:
  self: CatsEffectSuite =>

  // using "real" threads to have higher chance of parallelism

  def runParallel[A](block: => IO[A], blockn: IO[A]*): IO[List[A]] =
    IO.blocking {
      val code = block +: blockn
      val latch = CountDownLatch(1)
      val result = new AtomicReference[List[A]](Nil)
      val done = new CountDownLatch(code.size)
      code.foreach { ioa =>
        val t = new Thread(new Runnable {
          def run() =
            latch.await()
            val ta = Try(ioa.unsafeRunSync())
            ta.fold(_ => (), a => result.updateAndGet(list => a :: list))
            done.countDown()
            ta.fold(throw _, _ => ())
            ()
        })
        t.setDaemon(true)
        t.start()
      }
      latch.countDown()
      done.await(munitIOTimeout.toMillis, TimeUnit.MILLISECONDS)
      result.get()
    }
