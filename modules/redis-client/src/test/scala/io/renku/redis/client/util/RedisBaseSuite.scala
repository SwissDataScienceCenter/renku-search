package io.renku.redis.client.util

import cats.effect.*

import dev.profunktor.redis4cats.Redis
import dev.profunktor.redis4cats.connection.RedisClient
import dev.profunktor.redis4cats.data.RedisCodec
import dev.profunktor.redis4cats.effect.Log as RedisLog
import io.renku.redis.client.RedisQueueClientImpl
import io.renku.search.LoggingConfigure
import munit.*

trait RedisBaseSuite
    extends RedisServerSuite
    with LoggingConfigure
    with CatsEffectFixtures:

  given RedisLog[IO] = new RedisLog {
    def debug(msg: => String): IO[Unit] = scribe.cats.io.debug(msg)
    def error(msg: => String): IO[Unit] = scribe.cats.io.error(msg)
    def info(msg: => String): IO[Unit] = scribe.cats.io.info(msg)
  }

  val redisClientsR: Resource[IO, RedisClients] =
    for
      config <- Resource.eval(IO(redisServer()))
      lc <- RedisClient[IO]
        .from(s"redis://${config.host.value}:${config.port.value}")
      cmds <- Redis[IO].fromClient(lc, RedisCodec.Utf8)
      qc = new RedisQueueClientImpl[IO](lc)
    yield RedisClients(config, lc, cmds, qc)

  val redisClients = ResourceSuiteLocalFixture("all-redis-clients", redisClientsR)

  val redisClearAll: IO[Unit] =
    IO(redisClients()).flatMap(_.commands.flushAll)
