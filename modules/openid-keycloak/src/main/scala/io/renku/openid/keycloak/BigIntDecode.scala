package io.renku.openid.keycloak

import scodec.bits.Bases.Alphabets
import scodec.bits.ByteVector

private object BigIntDecode:

  def apply(num: String): Either[String, BigInt] =
    ByteVector
      .fromBase64Descriptive(num, Alphabets.Base64UrlNoPad)
      .map(bv => BigInt(1, bv.toArray))

  def decode(num: String): Either[JwtError, BigInt] =
    apply(num).left.map(msg => JwtError.BigIntDecodeError(num, msg))
