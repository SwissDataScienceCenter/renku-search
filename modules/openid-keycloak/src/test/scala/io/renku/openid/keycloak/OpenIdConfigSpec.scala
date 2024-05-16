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

import munit.FunSuite
import scala.io.Source
import io.bullet.borer.Json
import org.http4s.implicits.*

class OpenIdConfigSpec extends FunSuite:

  val configEndpointData = Source.fromResource("openid-configuration.json").mkString

  test("parse json"):
    val decoded = Json.decode(configEndpointData.getBytes()).to[OpenIdConfig].value
    assertEquals(
      decoded.authorizationEndpoint,
      uri"https://ci-renku-3622.dev.renku.ch/auth/realms/Renku/protocol/openid-connect/auth"
    )
    assertEquals(
      decoded.issuer,
      uri"https://ci-renku-3622.dev.renku.ch/auth/realms/Renku"
    )
    assertEquals(
      decoded.jwksUri,
      uri"https://ci-renku-3622.dev.renku.ch/auth/realms/Renku/protocol/openid-connect/certs"
    )
    assert(decoded.authorizationSigningAlgSupported.contains("RS512"))
