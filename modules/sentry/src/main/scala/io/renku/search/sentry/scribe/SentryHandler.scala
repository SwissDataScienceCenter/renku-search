package io.renku.search.sentry.scribe

import java.time.Instant

import cats.effect.*
import cats.effect.unsafe.IORuntime

import _root_.scribe.LogRecord
import _root_.scribe.format.Formatter
import _root_.scribe.handler.LogHandler
import _root_.scribe.output.LogOutput
import _root_.scribe.output.format.OutputFormat
import _root_.scribe.throwable.TraceLoggableMessage
import _root_.scribe.writer.Writer
import io.renku.search.sentry.*

object SentryHandler:

  def apply(sentry: Sentry[IO], minimumLevel: Level = Level.Warn)(using
      IORuntime
  ): LogHandler =
    LogHandler(
      writer = SentryWriter(sentry),
      formatter = Formatter.classic,
      minimumLevel = Some(minimumLevel.toScribe)
    )

  private class SentryWriter(sentry: Sentry[IO])(using IORuntime) extends Writer {
    def write(record: LogRecord, output: LogOutput, outputFormat: OutputFormat): Unit =
      val ev = SentryEvent(
        timestamp = Instant.ofEpochMilli(record.timeStamp),
        level = level(record.level),
        message = output.plainText,
        error = record.messages.collectFirst { case m: TraceLoggableMessage =>
          m.throwable
        },
        loggerName = Some(record.className),
        extras = record.data.view.mapValues(f => f().toString()).toMap
      )
      sentry.capture(ev).unsafeRunAndForget()

    private def level(l: _root_.scribe.Level): Level =
      l match
        case _root_.scribe.Level.Debug => Level.Debug
        case _root_.scribe.Level.Info  => Level.Info
        case _root_.scribe.Level.Warn  => Level.Warn
        case _root_.scribe.Level.Error => Level.Error
  }
