package io.renku.search.provision.metrics

import io.prometheus.client.Gauge
import io.renku.redis.client.QueueName

private class QueueSizeGauge extends QueueGauge:

  private val underlying =
    Gauge
      .build()
      .name("redis_stream_size")
      .help("Total number of items in a stream")
      .labelNames("queue_name")
      .create()

  override val asJCollector: Gauge = underlying

  override def set(q: QueueName, v: Double): Unit =
    underlying.labels(q.name).set(v)
