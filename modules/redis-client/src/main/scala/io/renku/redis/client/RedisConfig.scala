package io.renku.redis.client

import scala.concurrent.duration.FiniteDuration

import dev.profunktor.redis4cats.connection.RedisURI
import io.lettuce.core.RedisURI as JRedisURI

final case class RedisConfig(
    host: RedisHost,
    port: RedisPort,
    sentinel: Boolean = false,
    maybeDB: Option[RedisDB] = None,
    maybePassword: Option[RedisPassword] = None,
    maybeMasterSet: Option[RedisMasterSet] = None,
    connectionRefreshInterval: FiniteDuration
):
  lazy val asRedisUri: RedisURI =
    val builder = JRedisURI.Builder.redis(host.value, port.value)
    maybePassword.map(_.value.toCharArray).fold(builder)(builder.withPassword)
    maybeDB.map(_.value).fold(builder)(builder.withDatabase)
    RedisURI.fromUnderlying(builder.build())

opaque type RedisHost = String
object RedisHost {
  def apply(v: String): RedisHost = v
  extension (self: RedisHost) def value: String = self
}

opaque type RedisPort = Int
object RedisPort {
  def apply(v: Int): RedisPort = v
  extension (self: RedisPort) def value: Int = self
}

opaque type RedisDB = Int
object RedisDB {
  def apply(v: Int): RedisDB = v
  extension (self: RedisDB) def value: Int = self
}

opaque type RedisPassword = String
object RedisPassword {
  def apply(v: String): RedisPassword = v
  extension (self: RedisPassword) def value: String = self
}

opaque type RedisMasterSet = String
object RedisMasterSet {
  def apply(v: String): RedisMasterSet = v
  extension (self: RedisMasterSet) def value: String = self
}
