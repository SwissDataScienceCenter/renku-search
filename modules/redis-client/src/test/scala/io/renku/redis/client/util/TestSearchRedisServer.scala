package io.renku.redis.client.util

import cats.effect.{ExitCode, IO, IOApp}

import io.renku.servers.RedisServer

/** This is a utility to start a Redis server for manual testing */
object TestSearchRedisServer extends IOApp:

  override def run(args: List[String]): IO[ExitCode] =
    (IO(RedisServer.start()) >> IO.never[ExitCode]).as(ExitCode.Success)
