package io.renku.search.sentry

import java.time.Instant

import cats.Functor
import cats.effect.*
import cats.syntax.all.*

import io.sentry.SentryEvent as JSentryEvent
import io.sentry.protocol.Message

final case class SentryEvent(
    timestamp: Instant,
    level: Level,
    message: String,
    loggerName: Option[String] = None,
    error: Option[Throwable] = None,
    tags: Map[TagName, TagValue] = Map.empty,
    extras: Map[String, String] = Map.empty
):
  def withTimestamp(ts: Instant): SentryEvent =
    copy(timestamp = ts)

  def withLogger(name: String): SentryEvent =
    copy(loggerName = Some(name))

  def withLevel(lvl: Level): SentryEvent =
    copy(level = lvl)

  def withMessage(msg: String): SentryEvent =
    copy(message = msg)

  def withError(ex: Throwable): SentryEvent =
    copy(error = Some(ex))

  def withTag(name: TagName, value: TagValue): SentryEvent =
    copy(tags = tags.updated(name, value))

  def withExtra(key: String, value: String): SentryEvent =
    copy(extras = extras.updated(key, value))

  private[sentry] lazy val toEvent: JSentryEvent = {
    val ev = new JSentryEvent(java.util.Date.from(timestamp))
    loggerName.foreach(ev.setLogger)
    ev.setLevel(level.toSentry)
    val msg = new Message()
    msg.setFormatted(message)
    ev.setMessage(msg)
    error.foreach(ev.setThrowable)
    tags.foreach { case (k, v) => ev.setTag(k.value, v.value) }
    extras.foreach { case (k, v) => ev.setExtra(k, v) }
    ev
  }

object SentryEvent:
  def create[F[_]: Clock: Functor](level: Level, msg: String): F[SentryEvent] =
    Clock[F].realTimeInstant.map(now => SentryEvent(now, level, msg))
