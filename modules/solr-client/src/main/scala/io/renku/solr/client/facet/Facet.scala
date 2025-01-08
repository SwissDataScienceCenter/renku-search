package io.renku.solr.client.facet

import cats.data.NonEmptyList

import io.renku.solr.client.schema.FieldName

enum Facet:
  // https://solr.apache.org/guide/solr/latest/query-guide/json-facet-api.html#terms-facet
  case Terms(
      name: FieldName,
      field: FieldName,
      limit: Option[Int] = None,
      minCount: Option[Int] = None,
      method: Option[FacetAlgorithm] = None,
      missing: Boolean = false,
      numBuckets: Boolean = false,
      allBuckets: Boolean = false
  )

  // https://solr.apache.org/guide/solr/latest/query-guide/json-facet-api.html#range-facet
  case ArbitraryRange(
      name: FieldName,
      field: FieldName,
      ranges: NonEmptyList[FacetRange]
  )
