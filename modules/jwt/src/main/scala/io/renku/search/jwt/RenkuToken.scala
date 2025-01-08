package io.renku.search.jwt

import java.time.Instant

import io.bullet.borer.NullOptions.given
import io.bullet.borer.derivation.MapBasedCodecs
import io.bullet.borer.derivation.key
import io.bullet.borer.{Decoder, Encoder}
import io.renku.search.jwt.RenkuToken.{Access, AccountRoles}

final case class RenkuToken(
    @key("exp") expirationTime: Option[Instant] = None,
    @key("iat") issuedAt: Option[Instant] = None,
    @key("nbf") notBefore: Option[Instant] = None,
    @key("auth_time") authTime: Option[Instant] = None,
    @key("jti") jwtId: Option[String] = None,
    @key("iss") issuer: Option[String] = None,
    @key("sub") subject: Option[String] = None,
    @key("typ") tokenType: Option[String] = None,
    @key("realm_access") realmAccess: Option[Access] = None,
    @key("resource_access") resourceAccess: Option[AccountRoles] = None,
    @key("scope") scopeStr: Option[String] = None,
    name: Option[String] = None,
    email: Option[String] = None,
    @key("email_verified") emailVerified: Boolean = false,
    groups: Set[String] = Set.empty,
    @key("preferred_username") preferredUsername: Option[String] = None
):

  lazy val isAdmin =
    realmAccess.exists(_.roles.contains("renku-admin"))

object RenkuToken:
  final case class Access(roles: Set[String] = Set.empty)
  final case class AccountRoles(account: Access = Access())

  private given Decoder[Instant] = Decoder.forLong.map(Instant.ofEpochSecond(_))
  private given Encoder[Instant] = Encoder.forLong.contramap(_.getEpochSecond())

  private given Decoder[Access] = MapBasedCodecs.deriveDecoder
  private given Encoder[Access] = MapBasedCodecs.deriveEncoder
  private given Decoder[AccountRoles] = MapBasedCodecs.deriveDecoder
  private given Encoder[AccountRoles] = MapBasedCodecs.deriveEncoder

  given Decoder[RenkuToken] = MapBasedCodecs.deriveDecoder
  given Encoder[RenkuToken] = MapBasedCodecs.deriveEncoder
