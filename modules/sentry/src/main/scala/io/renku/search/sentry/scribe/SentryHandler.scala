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

package io.renku.search.sentry.scribe

import cats.effect.*
import scribe.LogRecord
import _root_.scribe.output.LogOutput
import _root_.scribe.writer.Writer
import _root_.scribe.handler.LogHandler
import io.renku.search.sentry.*
import _root_.scribe.output.format.OutputFormat
import java.time.Instant

object SentryHandler:

  def apply(sentry: Sentry[SyncIO]): LogHandler =
    LogHandler(writer = SentryWriter(sentry))

  private class SentryWriter(sentry: Sentry[SyncIO]) extends Writer {
    def write(record: LogRecord, output: LogOutput, outputFormat: OutputFormat): Unit =
      val ev = SentryEvent(
        timestamp = Instant.ofEpochMilli(record.timeStamp),
        level = level(record.level),
        message = output.plainText
      )
      sentry.capture(ev).unsafeRunSync()

    private def level(l: _root_.scribe.Level): Level =
      l match
        case _root_.scribe.Level.Debug => Level.Debug
        case _root_.scribe.Level.Info  => Level.Info
        case _root_.scribe.Level.Warn  => Level.Warn
        case _root_.scribe.Level.Error => Level.Error
  }
