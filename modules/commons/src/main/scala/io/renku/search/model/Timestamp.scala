package io.renku.search.model

import java.time.Instant
import java.time.temporal.ChronoUnit

import cats.Functor
import cats.effect.Clock
import cats.syntax.all.*

opaque type Timestamp = Instant

object Timestamp:
  def apply(v: Instant): Timestamp = v.truncatedTo(ChronoUnit.MILLIS)
  def now[F[_]: Clock: Functor]: F[Timestamp] =
    Clock[F].realTimeInstant.map(apply)

  extension (self: Timestamp) def toInstant: Instant = self
