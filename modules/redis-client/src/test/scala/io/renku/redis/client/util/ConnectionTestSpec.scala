package io.renku.redis.client.util

import cats.effect.*
import cats.implicits.*
import munit.CatsEffectSuite

class ConnectionTestSpec extends CatsEffectSuite with RedisSpec {

  test("connect to Redis") {
    withRedis().use { (redis: Command) =>
      for
        _ <- redis.set("foo", "123")
        x <- redis.get("foo")
        _ <- redis.setNx("foo", "should not happen")
        y <- redis.get("foo")
        _ <- IO(println(x === y)) // true
      yield ()
    }
  }
}
