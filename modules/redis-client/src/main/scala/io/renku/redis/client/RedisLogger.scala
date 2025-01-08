package io.renku.redis.client

import dev.profunktor.redis4cats.effect.Log
import scribe.Scribe

class RedisLogger[F[_]](scribe: Scribe[F]) extends Log[F]:

  override def debug(msg: => String): F[Unit] =
    scribe.trace(msg)

  override def info(msg: => String): F[Unit] =
    scribe.debug(msg)

  override def error(msg: => String): F[Unit] =
    scribe.error(msg)

object RedisLogger:
  def apply[F[_]: Scribe]: Log[F] = new RedisLogger[F](Scribe[F])
