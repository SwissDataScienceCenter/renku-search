package io.renku.search.provision.metrics

import io.prometheus.client.Gauge
import io.renku.search.metrics.Collector
import io.renku.search.model.EntityType
import io.renku.search.solr.documents.DocumentKind

private trait DocumentKindGauge extends Collector:
  val entityType: EntityType
  def set(l: DocumentKind, v: Double): Unit

private object DocumentKindGauge:
  def apply(entityType: EntityType): DocumentKindGauge =
    new DocumentKindGaugeImpl(entityType)

private class DocumentKindGaugeImpl(override val entityType: EntityType)
    extends DocumentKindGauge:

  private val underlying =
    Gauge
      .build()
      .name(s"solr_${entityType.name.toLowerCase}_by_kind")
      .help(s"Total number of '$entityType' entities by kind")
      .labelNames("kind")
      .create()

  override val asJCollector: Gauge = underlying

  override def set(l: DocumentKind, v: Double): Unit =
    underlying.labels(l.name).set(v)
