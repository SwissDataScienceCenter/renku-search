package io.renku.search.jwt

import java.time.Clock

import scala.util.Try

import io.bullet.borer.Json
import pdi.jwt.*

class JwtBorer(override val clock: Clock)
    extends JwtCore[JwtHeader, RenkuToken]
    with BorerCodec:
  private val noSigOptions = JwtOptions.DEFAULT.copy(signature = false)

  protected def parseHeader(header: String): JwtHeader =
    Json.decode(header.getBytes).to[JwtHeader].value

  protected def parseClaim(claim: String): RenkuToken =
    Json.decode(claim.getBytes).to[RenkuToken].value

  protected def extractAlgorithm(header: JwtHeader): Option[JwtAlgorithm] =
    header.algorithm
  protected def extractExpiration(claim: RenkuToken): Option[Long] =
    claim.expirationTime.map(_.getEpochSecond)
  protected def extractNotBefore(claim: RenkuToken): Option[Long] =
    claim.notBefore.map(_.getEpochSecond())

  def decodeAllNoSignatureCheck(token: String): Try[(JwtHeader, RenkuToken, String)] =
    decodeAll(token, noSigOptions)

  def decodeNoSignatureCheck(token: String): Try[RenkuToken] =
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
