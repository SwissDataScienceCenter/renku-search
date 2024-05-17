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

import java.time.*

import scala.io.Source

import io.bullet.borer.Json

trait JwtResources:

  val configEndpointData = Source.fromResource("openid-configuration.json").mkString
  val jwksJson = Source.fromResource("jwks.json").mkString
  // valid until 2024-05-15T14:47:26Z
  val jwToken = Source.fromResource("jwt-token1").mkString
  val jwTokenValidTime = Instant.parse("2024-05-15T13:47:26Z")

  lazy val jwks = Json.decode(jwksJson.getBytes).to[Jwks].value
  lazy val configData = Json.decode(configEndpointData.getBytes).to[OpenIdConfig].value

  val fixedClock = new Clock {
    def instant(): Instant = jwTokenValidTime
    def getZone(): ZoneId = ZoneId.of("UTC")
    override def withZone(zone: ZoneId): Clock = this
  }
