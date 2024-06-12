/*
 * Copyright 2024 Swiss Data Science Center (SDSC)
 * A partnership between École Polytechnique Fédérale de Lausanne (EPFL) and
 * Eidgenössische Technische Hochschule Zürich (ETHZ).
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.renku.openid.keycloak

import scala.concurrent.duration.*
import scala.concurrent.duration.FiniteDuration

import cats.effect.*
import cats.syntax.all.*

import io.renku.search.LoggingConfigure
import io.renku.search.TestClock
import io.renku.search.common.UrlPattern
import io.renku.search.http.borer.BorerEntityJsonCodec
import io.renku.search.jwt.JwtBorer
import munit.CatsEffectSuite
import org.http4s.client.Client
import org.http4s.dsl.io.*
import org.http4s.implicits.*
import org.http4s.{HttpRoutes, Uri}

class DefaultJwtVerifySpec
    extends CatsEffectSuite
    with LoggingConfigure
    with JwtResources
    with BorerEntityJsonCodec:

  val issuer: Uri = uri"https://ci-renku-3622.dev.renku.ch/auth/realms/Renku"
  val jwtConfig = JwtVerifyConfig.default.copy(allowedIssuerUrls =
    List(UrlPattern.unsafeFromString("*.*.renku.ch"))
  )

  extension [B](self: Either[JwtError, B])
    def isTooManyRequests = self match
      case Left(_: JwtError.TooManyValidationRequests) => true
      case _                                           => false

    def isForbiddenIssuer = self match
      case Left(_: JwtError.ForbiddenIssuer) => true
      case _                                 => false

  test("fetch jwks if validation fails, succeeding with new one"):
    val testClientRoutes = HttpRoutes.of[IO] {
      case GET -> Root / "auth" / "realms" / "Renku" / ".well-known" / "openid-configuration" =>
        Ok(configData)

      case GET -> Root / "auth" / "realms" / "Renku" / "protocol" / "openid-connect" / "certs" =>
        Ok(jwks)
    }
    val testClient = Client.fromHttpApp(testClientRoutes.orNotFound)
    val expected = JwtBorer(fixedClock).decodeNoSignatureCheck(jwToken).get
    val clock = TestClock.fixedAt(jwTokenValidTime)
    for
      state <- Ref[IO].of(DefaultJwtVerify.State())
      verifyer = new DefaultJwtVerify(testClient, state, clock, jwtConfig)
      result <- verifyer.verify(jwToken)
      claim = result.fold(throw _, identity)
      _ = assertEquals(claim, expected)
      time <- clock.monotonic
      _ <- assertIO(state.get.map(_.get(issuer).lastUpdate), time)
      _ <- assertIO(state.get.map(_.get(issuer).lastAccess), time)
    yield ()

  test("no api calls if cache is up to date"):
    val testClientRoutes = HttpRoutes.of[IO] {
      case GET -> Root / "auth" / "realms" / "Renku" / ".well-known" / "openid-configuration" =>
        fail("unexpected api call")

      case GET -> Root / "auth" / "realms" / "Renku" / "protocol" / "openid-connect" / "certs" =>
        fail("unexpected api call")
    }
    val testClient = Client.fromHttpApp(testClientRoutes.orNotFound)
    val expected = JwtBorer(fixedClock).decodeNoSignatureCheck(jwToken).get
    val clock = TestClock.fixedAt(jwTokenValidTime)
    for
      verifyerState <- Ref[IO].of(DefaultJwtVerify.State.of(issuer, jwks))
      verifyer = new DefaultJwtVerify(
        testClient,
        verifyerState,
        clock,
        jwtConfig
      )
      result <- verifyer.verify(jwToken)
      claim = result.fold(throw _, identity)
      _ = assertEquals(claim, expected)
    yield ()

  test(s"fail for too many validation requests"):
    val counter = Ref.unsafe[IO, Int](0)
    val testClientRoutes = HttpRoutes.of[IO] {
      case GET -> Root / "auth" / "realms" / "Renku" / ".well-known" / "openid-configuration" =>
        counter.update(_ + 1) >> Ok(configData)
      case GET -> Root / "auth" / "realms" / "Renku" / "protocol" / "openid-connect" / "certs" =>
        counter.update(_ + 1) >> Ok(jwks)
    }
    val testClient = Client.fromHttpApp(testClientRoutes.orNotFound)
    val clock = TestClock.fixedAt(jwTokenValidTime)
    val initialUpdate =
      FiniteDuration(jwTokenValidTime.minusSeconds(20).toEpochMilli(), "ms")
    for
      state <- Ref[IO].of(DefaultJwtVerify.State.of(issuer, lastUpdate = initialUpdate))
      verifyer = new DefaultJwtVerify(testClient, state, clock, jwtConfig)
      results <- (1 to 10).toList.parTraverse(_ => verifyer.verify(jwToken))
      numCalls <- counter.get
      _ = assert(results.forall(_.isTooManyRequests))
      _ = assertEquals(numCalls, 0)
    yield ()

  test("run requests after enough time passes"):
    val counter = Ref.unsafe[IO, Int](0)
    val testClientRoutes = HttpRoutes.of[IO] {
      case GET -> Root / "auth" / "realms" / "Renku" / ".well-known" / "openid-configuration" =>
        counter.update(_ + 1) >> Ok(configData)
      case GET -> Root / "auth" / "realms" / "Renku" / "protocol" / "openid-connect" / "certs" =>
        counter.update(_ + 1) >> Ok(jwks)
    }
    val testClient = Client.fromHttpApp(testClientRoutes.orNotFound)
    // min delay time is fixed to 1 minute
    val clock1 = TestClock.fixedAt(jwTokenValidTime.minusSeconds(120))
    val clock2 = TestClock.fixedAt(jwTokenValidTime.minusSeconds(40))
    val initialUpdate =
      FiniteDuration(jwTokenValidTime.minusSeconds(121).toEpochMilli(), "ms")
    for
      state <- Ref[IO].of(DefaultJwtVerify.State.of(issuer, lastUpdate = initialUpdate))
      verifyer1 = new DefaultJwtVerify(testClient, state, clock1, jwtConfig)
      result1 <- verifyer1.verify(jwToken)
      _ = assert(result1.isTooManyRequests)

      verifyer2 = new DefaultJwtVerify(testClient, state, clock2, jwtConfig)
      result2 <- verifyer2.verify(jwToken)
      _ = assert(result2.isRight)

      numCalls <- counter.get
      _ = assertEquals(numCalls, 2)
    yield ()

  test("stop on invalid issuer"):
    val testClient = Client.fromHttpApp(HttpRoutes.empty[IO].orNotFound)
    val allowedIssuers = List(UrlPattern.unsafeFromString("*.myserver.com"))
    for
      verifyer <- DefaultJwtVerify(
        testClient,
        TestClock.fixedAt(jwTokenValidTime),
        JwtVerifyConfig.default.copy(allowedIssuerUrls = allowedIssuers)
      )
      result <- verifyer.verify(jwToken)
      _ = assert(result.isForbiddenIssuer)
    yield ()

  test("scope jwks by issuer uri"):
    val issuer2: Uri = uri"https://ci-renku-3581.dev.renku.ch/auth/realms/Renku"
    val testClientRoutes = HttpRoutes.of[IO] {
      case req @ GET -> Root / "auth" / "realms" / "Renku" / ".well-known" / "openid-configuration" =>
        req.uri.host.map(_.value) match
          case Some("ci-renku-3581.dev.renku.ch") => Ok(configData2)
          case Some("ci-renku-3622.dev.renku.ch") => Ok(configData)
          case _                                  => NotFound()

      case req @ GET -> Root / "auth" / "realms" / "Renku" / "protocol" / "openid-connect" / "certs" =>
        req.uri.host.map(_.value) match
          case Some("ci-renku-3581.dev.renku.ch") => Ok(jwks2)
          case Some("ci-renku-3622.dev.renku.ch") => Ok(jwks)
          case _                                  => NotFound()
    }
    val expected = Map(
      issuer.renderString -> jwks,
      issuer2.renderString -> jwks2
    )
    val clock = TestClock.fixedAt(jwTokenValidTime)
    val testClient = Client.fromHttpApp(testClientRoutes.orNotFound)
    for
      state <- Ref[IO].of(DefaultJwtVerify.State())
      verifyer = new DefaultJwtVerify(testClient, state, clock, jwtConfig)
      _ <- verifyer.verify(jwToken)
      res <- verifyer.verify(jwToken2)
      data <- state.get
      actual = data.jwks.view.mapValues(_.jwks).toMap
      _ = assertEquals(actual, expected)
    yield ()
