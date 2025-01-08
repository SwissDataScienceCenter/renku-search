package io.renku.search.api

import cats.effect.IO

import io.bullet.borer.Decoder
import io.bullet.borer.derivation.MapBasedCodecs
import io.renku.search.api.SearchRoutesSpec.SpecJson
import io.renku.search.api.data.SearchResult
import io.renku.search.common.CurrentVersion
import munit.{AnyFixture, CatsEffectSuite}
import org.http4s.Method
import org.http4s.client.Client
import org.http4s.implicits.*

class SearchRoutesSpec extends CatsEffectSuite with SearchRoutesSuite:

  override def munitFixtures: Seq[AnyFixture[?]] =
    List(solrServer, searchHttpRoutes)

  def makeClient = IO(searchHttpRoutes()).map { r =>
    Client.fromHttpApp(r.orNotFound)
  }

  test("default query"):
    for
      client <- makeClient
      req = Method.GET(uri"/api/search/query")
      _ <- client.expect[SearchResult](req)
    yield ()

  test("not found query"):
    for
      client <- makeClient
      req = Method.GET(uri"/app/search/query")
      res <- client.expectOption[SearchResult](req)
      _ = assertEquals(res, None)
    yield ()

  test("legacy query"):
    for
      client <- makeClient
      req = Method.GET(uri"/search")
      _ <- client.expect[SearchResult](req)
    yield ()

  test("legacy query (2)"):
    for
      client <- makeClient
      req = Method.GET(uri"/search/query")
      _ <- client.expect[SearchResult](req)
    yield ()

  test("spec.json"):
    for
      client <- makeClient
      req = Method.GET(uri"/api/search/spec.json")
      spec <- client.expect[SpecJson](req)
      _ = assertEquals(spec.openapi, "3.1.0")
      _ = assertEquals(spec.info.get("title"), Some("Renku Search API"))
      _ = assertEquals(spec.servers.head.get("url"), Some("/api/search"))
    yield ()

  test("spec.json (legacy)"):
    for
      client <- makeClient
      req = Method.GET(uri"/search/spec.json")
      spec <- client.expect[SpecJson](req)
      _ = assertEquals(spec.openapi, "3.1.0")
      _ = assertEquals(spec.info.get("title"), Some("Renku Search API"))
      _ = assertEquals(spec.servers.head.get("url"), Some("/api/search"))
    yield ()

  test("default version"):
    for
      client <- makeClient
      req = Method.GET(uri"/api/search/version")
      _ <- client.expect[CurrentVersion](req)
    yield ()

  test("legacy version"):
    for
      client <- makeClient
      req = Method.GET(uri"/search/version")
      _ <- client.expect[CurrentVersion](req)
    yield ()

  test("ping"):
    for
      client <- makeClient
      req = Method.GET(uri"/ping")
      s <- client.successString(req)
      _ = assertEquals(s, "pong")
    yield ()

  test("version (operation routes)"):
    for
      client <- makeClient
      req = Method.GET(uri"/version")
      _ <- client.expect[CurrentVersion](req)
    yield ()

  test("metrics"):
    for
      client <- makeClient
      req = Method.GET(uri"/metrics")
      str <- client.successString(req)
      _ = assert(str.nonEmpty)
    yield ()

object SearchRoutesSpec:
  final case class SpecJson(
      openapi: String,
      info: Map[String, String],
      servers: List[Map[String, String]]
  )
  object SpecJson:
    given Decoder[SpecJson] = MapBasedCodecs.deriveDecoder
