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

import org.http4s.client.middleware.RetryPolicy
import org.http4s.headers.`Idempotency-Key`
import org.http4s.{Request, Response, Status}

final class RetryStrategy[F[_]](retriableStatuses: Set[Status]) {

  def policy(cfg: RetryConfig): RetryPolicy[F] =
    RetryPolicy(
      backoff = RetryPolicy.exponentialBackoff(cfg.maxWait, cfg.maxRetries),
      retriable = isRetryRequest
    )

  private def isRetryRequest(
      req: Request[F],
      result: Either[Throwable, Response[F]]
  ): Boolean =
    (req.method.isIdempotent || req.headers.contains[`Idempotency-Key`]) &&
      RetryPolicy.isErrorOrStatus(result, retriableStatuses)
}

object RetryStrategy:
  def apply[F[_]](retriableStatuses: Set[Status]): RetryStrategy[F] =
    new RetryStrategy[F](retriableStatuses)

  def default[F[_]]: RetryStrategy[F] =
    apply(RetryPolicy.RetriableStatuses)

  def defaultPolicy[F[_]](cfg: RetryConfig): RetryPolicy[F] = default[F].policy(cfg)
