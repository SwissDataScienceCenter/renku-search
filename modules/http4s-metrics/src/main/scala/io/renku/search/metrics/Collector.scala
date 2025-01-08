package io.renku.search.metrics

import io.prometheus.client.Collector as JCollector

trait Collector:
  def asJCollector: JCollector
