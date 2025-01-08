package io.renku.redis.client.util

import cats.effect.*

import dev.profunktor.redis4cats.RedisCommands
import dev.profunktor.redis4cats.connection.RedisClient
import io.renku.redis.client.RedisConfig
import io.renku.redis.client.RedisQueueClient

final case class RedisClients(
    config: RedisConfig,
    lowLevel: RedisClient,
    commands: RedisCommands[IO, String, String],
    queueClient: RedisQueueClient[IO]
)
