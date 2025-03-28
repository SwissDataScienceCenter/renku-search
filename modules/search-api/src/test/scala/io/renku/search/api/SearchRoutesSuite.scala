package io.renku.search.api

import scala.concurrent.duration.*

import cats.effect.*

import com.comcast.ip4s.*
import io.renku.openid.keycloak.JwtVerifyConfig
import io.renku.search.http.HttpClientDsl
import io.renku.search.http.HttpServerConfig
import io.renku.search.http.borer.BorerEntityJsonCodec
import io.renku.search.sentry.SentryConfig
import io.renku.search.solr.client.SearchSolrSuite
import io.renku.solr.client.SolrConfig
import org.http4s.HttpRoutes
import org.http4s.Request
import org.http4s.client.Client
import org.http4s.headers.`Content-Type`
import org.http4s.implicits.*

trait SearchRoutesSuite
    extends SearchSolrSuite
    with HttpClientDsl[IO]
    with BorerEntityJsonCodec:

  val serviceRoutesR: Resource[IO, ServiceRoutes[IO]] =
    solrClientWithSchemaR.flatMap { solrClient =>
      val cfg = SearchRoutesSuite.testConfig.copy(
        solrConfig = solrClient.config,
        verbosity = defaultVerbosity
      )
      ServiceRoutes[IO](cfg, Microservice.pathPrefix)
    }

  val searchHttpRoutesR: Resource[IO, HttpRoutes[IO]] =
    serviceRoutesR.flatMap(SearchServer.makeHttpRoutes)

  val searchHttpRoutes =
    ResourceSuiteLocalFixture("search-http-routes", searchHttpRoutesR)

  extension (self: Client[IO])
    def successString(req: Request[IO]) =
      self.run(req).use { r =>
        if (r.status.isSuccess) r.body.through(fs2.text.utf8.decode).compile.string
        else
          IO.raiseError(
            new Exception(
              s"Invalid status '${r.status}' for req '${req.uri.renderString}'"
            )
          )
      }

    def printResponse(req: Request[IO]) =
      self.run(req).use { r =>
        for
          _ <- IO.println(s"==== REQ: ${req.uri.renderString} ====")
          _ <- IO.println(s"  Status: ${r.status}")
          _ <- IO.println(s"  Content-Type: ${r.headers.get[`Content-Type`]}")
          _ <- IO.println(s"----- Body ----")
          _ <- r.body.through(fs2.text.utf8.decode).compile.string.flatMap(IO.println)
        yield ()
      }

object SearchRoutesSuite:

  private val testConfig = SearchApiConfig(
    // solrConfig will be replaced with the one created by the test
    // fixture
    solrConfig = SolrConfig(
      baseUrl = uri"",
      core = "",
      maybeUser = None,
      logMessageBodies = false
    ),
    // http config is not used
    httpServerConfig = HttpServerConfig(
      bindAddress = ipv4"0.0.0.0",
      port = port"9999",
      shutdownTimeout = 0.millis
    ),
    jwtVerifyConfig = JwtVerifyConfig(
      minRequestDelay = 1.minute,
      enableSignatureValidation = false,
      openIdConfigPath = "/",
      allowedIssuerUrls = Nil
    ),
    sentryConfig = SentryConfig.disabled,
    verbosity = 1
  )
