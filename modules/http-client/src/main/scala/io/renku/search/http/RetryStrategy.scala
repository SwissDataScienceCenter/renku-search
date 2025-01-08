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
