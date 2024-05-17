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

package io.renku.search.jwt

import java.time.Clock

import scala.util.Try

import io.bullet.borer.Json
import pdi.jwt.*

class JwtBorer(override val clock: Clock)
    extends JwtCore[JwtHeader, JwtClaim]
    with BorerCodec:
  private val noSigOptions = JwtOptions.DEFAULT.copy(signature = false)

  protected def parseHeader(header: String): JwtHeader =
    Json.decode(header.getBytes).to[JwtHeader].value

  protected def parseClaim(claim: String): JwtClaim =
    Json.decode(claim.getBytes).to[JwtClaim].value

  protected def extractAlgorithm(header: JwtHeader): Option[JwtAlgorithm] =
    header.algorithm
  protected def extractExpiration(claim: JwtClaim): Option[Long] = claim.expiration
  protected def extractNotBefore(claim: JwtClaim): Option[Long] = claim.notBefore

  def decodeAllNoSignatureCheck(token: String): Try[(JwtHeader, JwtClaim, String)] =
    decodeAll(token, noSigOptions)

  def decodeNoSignatureCheck(token: String): Try[JwtClaim] =
    decode(token, noSigOptions)

object JwtBorer extends JwtBorer(Clock.systemUTC()):
  def apply(clock: Clock): JwtBorer = new JwtBorer(clock)

  def create[F[_]: cats.effect.Clock]: F[JwtBorer] =
    val c = cats.effect.Clock[F]
    c.applicative.map(c.realTimeInstant) { rt =>
      new JwtBorer(new Clock {
        def instant(): java.time.Instant = rt
        def getZone(): java.time.ZoneId = java.time.ZoneId.of("UTC")
        override def withZone(zone: java.time.ZoneId): Clock = this
      })
    }

  def readHeader(token: String): Either[Throwable, JwtHeader] =
    val h64 = token.takeWhile(_ != '.')
    Json
      .decode(JwtBase64.decode(h64))
      .to[JwtHeader]
      .valueEither
