package io.renku.search.provision.metrics

import io.renku.redis.client.QueueName
import io.renku.search.metrics.Collector

private trait QueueGauge extends Collector:
  def set(q: QueueName, v: Double): Unit
