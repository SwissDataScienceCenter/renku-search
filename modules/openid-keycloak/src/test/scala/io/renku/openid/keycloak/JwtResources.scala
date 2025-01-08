package io.renku.openid.keycloak

import java.time.*

import scala.io.Source

import io.bullet.borer.Json

trait JwtResources:

  val configEndpointData = Source.fromResource("openid-configuration.json").mkString
  val configEndpointData2 = Source.fromResource("openid-configuration2.json").mkString

  val jwksJson = Source.fromResource("jwks.json").mkString
  val jwks2Json = Source.fromResource("jwks2.json").mkString

  // valid until 2024-05-15T14:47:26Z
  val jwToken = Source.fromResource("jwt-token1").mkString
  val jwToken2 = Source.fromResource("jwt-token2").mkString
  val jwTokenValidTime = Instant.parse("2024-05-15T13:47:26Z")

  lazy val jwks = Json.decode(jwksJson.getBytes).to[Jwks].value
  lazy val jwks2 = Json.decode(jwks2Json.getBytes).to[Jwks].value

  lazy val configData = Json.decode(configEndpointData.getBytes).to[OpenIdConfig].value
  lazy val configData2 = Json.decode(configEndpointData2.getBytes).to[OpenIdConfig].value

  val fixedClock = new Clock {
    def instant(): Instant = jwTokenValidTime
    def getZone(): ZoneId = ZoneId.of("UTC")
    override def withZone(zone: ZoneId): Clock = this
  }
