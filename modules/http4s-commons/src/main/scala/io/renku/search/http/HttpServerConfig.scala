package io.renku.search.http

import com.comcast.ip4s.{Ipv4Address, Port}
import scala.concurrent.duration.Duration

final case class HttpServerConfig(
    bindAddress: Ipv4Address,
    port: Port,
    shutdownTimeout: Duration
):
  override def toString = s"Http server @ ${bindAddress}:$port"
