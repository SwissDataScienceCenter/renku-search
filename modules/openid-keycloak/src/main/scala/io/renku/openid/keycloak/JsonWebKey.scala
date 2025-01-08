package io.renku.openid.keycloak

import java.security.PublicKey

import io.bullet.borer.*
import io.bullet.borer.derivation.MapBasedCodecs
import io.bullet.borer.derivation.key

sealed trait JsonWebKey:
  def keyId: KeyId
  def keyType: KeyType
  def keyUse: KeyUse
  def algorithm: String
  def toPublicKey: Either[JwtError, PublicKey]

object JsonWebKey:

  @key("RSA")
  final case class Rsa(
      @key("kid") keyId: KeyId,
      @key("alg") algorithm: String,
      @key("use") keyUse: KeyUse,
      @key("n") modulus: String,
      @key("e") exponent: String,
      @key("x5c") certificateChain: List[String] = Nil,
      @key("x5t") certificateThumbPrint: Option[String] = None
  ) extends JsonWebKey:
    val keyType = KeyType.RSA
    lazy val toPublicKey: Either[JwtError, PublicKey] =
      RsaPublicKey.create(this)

  @key("EC")
  final case class Ec(
      @key("kid") keyId: KeyId,
      @key("alg") algorithm: String,
      @key("use") keyUse: KeyUse,
      @key("x") x: String,
      @key("y") y: String,
      @key("crv") curve: Curve
  ) extends JsonWebKey:
    val keyType = KeyType.EC
    lazy val toPublicKey: Either[JwtError, PublicKey] =
      EcPublicKey.create(this)

  @key("OKP")
  final case class Okp(
      @key("kid") keyId: KeyId,
      @key("alg") algorithm: String,
      @key("use") keyUse: KeyUse,
      @key("x") x: String,
      @key("crv") curve: Curve
  ) extends JsonWebKey:
    val keyType = KeyType.OKP
    lazy val toPublicKey: Either[JwtError, PublicKey] = Left(
      JwtError.UnsupportedPublicKey(keyType)
    )

  private given AdtEncodingStrategy =
    AdtEncodingStrategy.flat(typeMemberName = "kty")
  given Decoder[JsonWebKey] = {
    given Decoder[Ec] = MapBasedCodecs.deriveDecoder
    given Decoder[Rsa] = MapBasedCodecs.deriveDecoder
    given Decoder[Okp] = MapBasedCodecs.deriveDecoder

    MapBasedCodecs.deriveDecoder[JsonWebKey]
  }

  given Encoder[JsonWebKey] = {
    given Encoder[Ec] = MapBasedCodecs.deriveEncoder
    given Encoder[Rsa] = MapBasedCodecs.deriveEncoder
    given Encoder[Okp] = MapBasedCodecs.deriveEncoder

    MapBasedCodecs.deriveEncoder
  }
