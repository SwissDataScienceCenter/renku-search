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

package io.renku.search.provision

import cats.syntax.all.*
import cats.effect.*
import cats.effect.kernel.Fiber
import cats.effect.kernel.Ref
import scala.concurrent.duration.FiniteDuration

trait BackgroundProcessManage[F[_]]:
  def register(name: String, process: F[Unit]): F[Unit]

  /** Starts all registered tasks in the background, represented by `F[Unit]`. */
  def background: Resource[F, F[Unit]]

  /** Same as `.background.useForever`   */
  def startAll: F[Nothing]

object BackgroundProcessManage:
  type Process[F[_]] = Fiber[F, Throwable, Unit]

  private case class State[F[_]](tasks: Map[String, F[Unit]]):
    def put(name: String, p: F[Unit]): State[F] =
      State(tasks.updated(name, p))

    def getTasks: List[F[Unit]] = tasks.values.toList

  private object State:
    def empty[F[_]]: State[F] = State[F](Map.empty)

  def apply[F[_]: Async](
      retryDelay: FiniteDuration,
      maxRetries: Option[Int] = None
  ): F[BackgroundProcessManage[F]] =
    val logger = scribe.cats.effect[F]
    Ref.of[F, State[F]](State.empty[F]).map { state =>
      new BackgroundProcessManage[F] {
        def register(name: String, task: F[Unit]): F[Unit] =
          state.update(_.put(name, wrapTask(name, task)))

        def startAll: F[Nothing] =
          background.useForever

        def background: Resource[F, F[Unit]] =
          for {
            ts <- Resource.eval(state.get.map(_.getTasks))
            x <- ts.traverse(t => Async[F].background(t))
            y = x.traverse_(_.map(_.embed(logger.info(s"Got cancelled"))))
          } yield y

        def wrapTask(name: String, task: F[Unit]): F[Unit] =
          def run(c: Ref[F, Long]): F[Unit] =
            logger.info(s"Starting process for: ${name}") >>
              task.handleErrorWith { err =>
                c.updateAndGet(_ + 1).flatMap {
                  case n if maxRetries.exists(_ <= n) =>
                    logger.error(
                      s"Max retries ($maxRetries) for process ${name} exceeded"
                    ) >> Async[F].raiseError(err)
                  case n =>
                    val maxRetriesLabel = maxRetries.map(m => s"/$m").getOrElse("")
                    logger.error(
                      s"Starting process for '${name}' failed ($n$maxRetriesLabel), retrying",
                      err
                    ) >> Async[F].delayBy(run(c), retryDelay)
                }
              }
          Ref.of[F, Long](0).flatMap(run)
      }
    }
