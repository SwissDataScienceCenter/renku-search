package io.renku.search.http

import scribe.Scribe

final class LoggerProxy[F[_]](logger: Scribe[F])
    extends org.typelevel.log4cats.Logger[F] {
  override def error(message: => String): F[Unit] = logger.error(message)

  override def warn(message: => String): F[Unit] = logger.warn(message)

  override def info(message: => String): F[Unit] = logger.info(message)

  override def debug(message: => String): F[Unit] = logger.debug(message)

  override def trace(message: => String): F[Unit] = logger.trace(message)

  override def error(t: Throwable)(message: => String): F[Unit] = logger.error(message, t)

  override def warn(t: Throwable)(message: => String): F[Unit] = logger.warn(message, t)

  override def info(t: Throwable)(message: => String): F[Unit] = logger.info(message, t)

  override def debug(t: Throwable)(message: => String): F[Unit] = logger.debug(message, t)

  override def trace(t: Throwable)(message: => String): F[Unit] = logger.trace(message, t)
}

object LoggerProxy:
  def apply[F[_]](delegate: Scribe[F]): org.typelevel.log4cats.Logger[F] =
    new LoggerProxy[F](delegate)
