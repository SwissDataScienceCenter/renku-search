/*
 * Copyright 2024 Swiss Data Science Center (SDSC)
 * A partnership between École Polytechnique Fédérale de Lausanne (EPFL) and
 * Eidgenössische Technische Hochschule Zürich (ETHZ).
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

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
