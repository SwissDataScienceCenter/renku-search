package io.renku.solr.client.facet

import cats.data.NonEmptyList

import io.bullet.borer.Json
import io.renku.solr.client.schema.FieldName
import munit.FunSuite

class FacetsSpec extends FunSuite:

  test("encode multiple facets into object"):
    val terms1 = Facet.Terms(FieldName("type"), FieldName("_type"))
    val terms2 = Facet.Terms(FieldName("cat"), FieldName("category"))
    val json = Json.encode(Facets(terms1, terms2)).toUtf8String
    assertEquals(
      json,
      """{"type":{"type":"terms","field":"_type","missing":false,"numBuckets":false,"allBuckets":false},"cat":{"type":"terms","field":"category","missing":false,"numBuckets":false,"allBuckets":false}}"""
    )

  test("encode arbitrary range"):
    val range = Facet.ArbitraryRange(
      FieldName("stars"),
      FieldName("stars"),
      NonEmptyList.of(
        FacetRange(FacetRange.All, 100),
        FacetRange(100, 200),
        FacetRange(200, FacetRange.All)
      )
    )
    val json = Json.encode(Facets(range)).toUtf8String
    assertEquals(
      json,
      """{"stars":{"type":"range","field":"stars","ranges":[{"from":"*","to":100},{"from":100,"to":200},{"from":200,"to":"*"}]}}"""
    )
