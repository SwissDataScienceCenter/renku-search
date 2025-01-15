package io.renku.search.authzed

import io.grpc.ManagedChannelBuilder

final case class ChannelConfig(
    address: String,
    tlsMode: ChannelConfig.TlsMode,
    credentials: BearerToken
):

  def withToken(token: BearerToken): ChannelConfig =
    copy(credentials = token)

  private[authzed] def toUnderlying =
    ManagedChannelBuilder.forTarget(address).withTls(tlsMode)

object ChannelConfig:

  def plain(address: String, token: BearerToken): ChannelConfig =
    ChannelConfig(address, TlsMode.Plain, token)

  def tls(address: String, token: BearerToken): ChannelConfig =
    ChannelConfig(address, TlsMode.Tls, token)

  enum TlsMode:
    case Plain
    case Tls

extension (self: ManagedChannelBuilder[?])
  def withTls(mode: ChannelConfig.TlsMode): ManagedChannelBuilder[?] =
    mode match
      case ChannelConfig.TlsMode.Plain => self.usePlaintext()
      case ChannelConfig.TlsMode.Tls   => self.useTransportSecurity()
