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

package io.renku.search.http

import scala.concurrent.duration.Duration

import cats.effect.kernel.{Async, Resource}
import fs2.io.net.Network

import org.http4s.Status
import org.http4s.client.Client
import org.http4s.ember.client.EmberClientBuilder
import scribe.Scribe

final class ClientBuilder[F[_]: Async](
    delegate: EmberClientBuilder[F],
    middlewares: List[Client[F] => Client[F]]
) {
  def withLogger(l: Scribe[F]): ClientBuilder[F] =
    forward(_.withLogger(LoggerProxy(l)))

  def withTimeout(timeout: Duration): ClientBuilder[F] =
    forward(_.withTimeout(timeout).withIdleConnectionTime(timeout * 1.5))

  def withDefaultRetry(cfg: RetryConfig): ClientBuilder[F] =
    forward(_.withRetryPolicy(RetryStrategy.default[F].policy(cfg)))

  def withDefaultRetry(cfg: Option[RetryConfig]): ClientBuilder[F] =
    cfg match
      case Some(c) => withDefaultRetry(c)
      case None    => this

  def withRetry(
      retriableStatuses: Set[Status],
      cfg: RetryConfig
  ): ClientBuilder[F] =
    forward(_.withRetryPolicy(RetryStrategy(retriableStatuses).policy(cfg)))

  def withLogging(logBody: Boolean, logger: Scribe[F]): ClientBuilder[F] = {
    val mw = org.http4s.client.middleware.Logger[F](
      logHeaders = true,
      logBody = logBody,
      logAction = Some(logger.debug(_))
    )
    new ClientBuilder[F](delegate.withLogger(LoggerProxy(logger)), mw :: middlewares)
  }

  private def forward(
      f: EmberClientBuilder[F] => EmberClientBuilder[F]
  ): ClientBuilder[F] =
    new ClientBuilder[F](f(delegate), middlewares)

  def build: Resource[F, Client[F]] =
    delegate.build.map(c0 => middlewares.foldRight(c0)(_ apply _))

}

object ClientBuilder:
  def apply[F[_]: Async](b: EmberClientBuilder[F]): ClientBuilder[F] =
    new ClientBuilder[F](b, Nil)

  def default[F[_]: Async: Network]: ClientBuilder[F] =
    apply(EmberClientBuilder.default[F])

  extension [F[_]: Async](self: EmberClientBuilder[F])
    def lift: ClientBuilder[F] = new ClientBuilder[F](self, Nil)
