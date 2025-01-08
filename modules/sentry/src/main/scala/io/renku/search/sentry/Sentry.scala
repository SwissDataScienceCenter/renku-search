package io.renku.search.sentry

import cats.Applicative
import cats.effect.*
import cats.effect.std.Console
import cats.syntax.all.*

import io.sentry.Sentry as JSentry

trait Sentry[F[_]]:
  def capture(ev: SentryEvent): F[Unit]

object Sentry:

  def noop[F[_]: Applicative]: Sentry[F] =
    new Sentry[F] {
      def capture(ev: SentryEvent): F[Unit] = ().pure[F]
    }

  def stdout[F[_]: Console]: Sentry[F] =
    new Sentry[F] {
      def capture(ev: SentryEvent): F[Unit] = Console[F].println(ev.toString)
    }

  def apply[F[_]: Sync](cfg: SentryConfig): Resource[F, Sentry[F]] =
    cfg.toSentryOptions match
      case None => Resource.pure(noop[F])
      case Some(opts) =>
        Resource.make(Sync[F].blocking {
          // TODO figure out if there is a better way than this once-per-jvm static
          JSentry.init(opts)
          new Impl[F]
        })(_ => Sync[F].blocking(JSentry.close))

  final private class Impl[F[_]: Sync] extends Sentry[F] {
    def capture(ev: SentryEvent): F[Unit] =
      Sync[F].blocking(JSentry.captureEvent(ev.toEvent)).void
  }
