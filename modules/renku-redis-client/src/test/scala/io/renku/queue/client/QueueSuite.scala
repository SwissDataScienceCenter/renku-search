package io.renku.queue.client

import cats.effect.{IO, Resource}

import io.renku.redis.client.ClientId
import io.renku.redis.client.util.RedisBaseSuite

trait QueueSuite extends RedisBaseSuite:

  val queueClientR: Resource[IO, QueueClient[IO]] =
    redisClientsR.map(c =>
      new QueueClientImpl[IO](c.queueClient, ClientId("search-provisioner"))
    )

  val queueClient = ResourceSuiteLocalFixture("queue-client", queueClientR)
