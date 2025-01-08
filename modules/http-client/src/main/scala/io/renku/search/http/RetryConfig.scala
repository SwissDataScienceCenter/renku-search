package io.renku.search.http

import scala.concurrent.duration.*

final case class RetryConfig(maxWait: Duration, maxRetries: Int)

object RetryConfig:
  val default: RetryConfig = RetryConfig(50.seconds, 4)
