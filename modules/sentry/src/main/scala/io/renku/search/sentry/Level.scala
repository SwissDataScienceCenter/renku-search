package io.renku.search.sentry

import _root_.scribe.Level as ScribeLevel
import io.sentry.SentryLevel

enum Level:
  case Error
  case Warn
  case Info
  case Debug

  private[sentry] def toSentry: SentryLevel = this match
    case Error => SentryLevel.ERROR
    case Warn  => SentryLevel.WARNING
    case Info  => SentryLevel.INFO
    case Debug => SentryLevel.DEBUG

  private[sentry] def toScribe: ScribeLevel = this match
    case Error => ScribeLevel.Error
    case Warn  => ScribeLevel.Warn
    case Info  => ScribeLevel.Info
    case Debug => ScribeLevel.Debug
