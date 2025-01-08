package io.renku.search.jwt

import cats.syntax.all.*

import io.bullet.borer.{Decoder, Reader}
import pdi.jwt.{JwtAlgorithm, JwtClaim, JwtHeader}

trait BorerCodec:
  given Decoder[JwtAlgorithm] =
    Decoder.forString.map(JwtAlgorithm.fromString)

  given Decoder[JwtHeader] = new Decoder[JwtHeader]:
    def read(r: Reader): JwtHeader =
      r.readMapStart()
      r.readUntilBreak(JwtHeader(None, None, None, None).withType) { h =>
        r.readString() match
          case "alg" =>
            val alg = r.read[JwtAlgorithm]()
            JwtHeader(alg.some, h.typ, h.contentType, h.keyId)
          case "typ" => h.withType(r.readString())
          case "cty" =>
            JwtHeader(h.algorithm, h.typ, r.readString().some, h.keyId)
          case "kid" => h.withKeyId(r.readString())
          case _ =>
            r.skipElement()
            h
      }

  given Decoder[JwtClaim] = new Decoder[JwtClaim]:
    def read(r: Reader): JwtClaim =
      r.readMapStart()
      r.readUntilBreak(JwtClaim()) { c =>
        r.readString() match
          case "iss" => c.copy(issuer = r.readStringOpt())
          case "sub" => c.copy(subject = r.readStringOpt())
          case "aud" => c.copy(audience = r.readSetStr())
          case "exp" => c.copy(expiration = r.readLongOpt())
          case "nbf" => c.copy(notBefore = r.readLongOpt())
          case "iat" => c.copy(issuedAt = r.readLongOpt())
          case "jti" => c.copy(jwtId = r.readStringOpt())
          case _ =>
            r.skipElement()
            c
      }

  extension (self: Reader)
    def readStringOpt(): Option[String] =
      if (self.tryReadNull()) None else self.readString().some

    def readLongOpt(): Option[Long] =
      if (self.tryReadNull()) None else self.readLong().some

    def readSetStr(): Option[Set[String]] =
      if (self.tryReadNull()) None
      else if (self.hasArrayStart) self.read[Set[String]]().some
      else Set(self.readString()).some

  extension (self: JwtClaim)
    def copy(
        issuer: Option[String] = self.issuer,
        subject: Option[String] = self.subject,
        audience: Option[Set[String]] = self.audience,
        expiration: Option[Long] = self.expiration,
        notBefore: Option[Long] = self.notBefore,
        issuedAt: Option[Long] = self.issuedAt,
        jwtId: Option[String] = self.jwtId
    ): JwtClaim =
      JwtClaim(
        self.content,
        issuer,
        subject,
        audience,
        expiration,
        notBefore,
        issuedAt,
        jwtId
      )
