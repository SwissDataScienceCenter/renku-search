package io.renku.search.provision.metrics

import io.renku.search.model.EntityType

object SolrMetrics:

  val allCollectors: Set[DocumentKindGauge] =
    EntityType.values.toSet.map(DocumentKindGauge.apply)
