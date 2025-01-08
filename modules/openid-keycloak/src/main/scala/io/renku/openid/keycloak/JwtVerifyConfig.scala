package io.renku.openid.keycloak

import scala.concurrent.duration.*

import io.renku.search.common.UrlPattern
import org.http4s.Uri

/** Configuration settings for [[JwtVerify]]
  *
  * @param minRequestDelay
  *   minimum delay between requests to fetch an JWKS
  * @param enableSignatureValidation
  *   whether to enable reaching to keycloak to fetch the public key for signature
  *   validation. If this is false, token signatures are NOT validated!
  * @param openIdConfigPath
  *   the uri path part after the realm that denotes the endpoint to get the configuration
  *   data from
  * @param allowedIssuerUrls
  *   a list of url patterns that are allowed to ask for JWKS
  */
final case class JwtVerifyConfig(
    minRequestDelay: FiniteDuration,
    enableSignatureValidation: Boolean,
    openIdConfigPath: String,
    allowedIssuerUrls: List[UrlPattern]
):

  def checkIssuerUrl(uri: Uri): Either[JwtError, Unit] =
    val uriStr = uri.renderString
    if (allowedIssuerUrls.exists(p => p.matches(uriStr))) Right(())
    else Left(JwtError.ForbiddenIssuer(uri, allowedIssuerUrls))

object JwtVerifyConfig:

  val default = JwtVerifyConfig(
    minRequestDelay = 1.minute,
    enableSignatureValidation = true,
    openIdConfigPath = ".well-known/openid-configuration",
    Nil
  )
