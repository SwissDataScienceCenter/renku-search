package io.renku.search

import java.time.{Clock as _, *}

import scala.concurrent.duration.FiniteDuration

import cats.Applicative
import cats.effect.*

object TestClock:
  extension (i: Instant)
    def toDuration: FiniteDuration = FiniteDuration(i.toEpochMilli(), "ms")

  def fixedAt(fixed: Instant): Clock[IO] =
    new Clock[IO] {
      val applicative: Applicative[IO] = Applicative[IO]
      val realTime: IO[FiniteDuration] = IO.pure(fixed.toDuration)
      val monotonic: IO[FiniteDuration] = realTime
      override def toString = s"FixedClock($fixed)"
    }

  /** Clock initially returning `start` and adding `interval` to subsequent calls. */
  def advanceBy(start: Instant, interval: FiniteDuration): Clock[IO] =
    new Clock[IO] {
      val counter = Ref.unsafe[IO, Long](0)
      val applicative: Applicative[IO] = Applicative[IO]
      val realTime: IO[FiniteDuration] =
        counter.getAndUpdate(_ + 1).map { n =>
          (interval * n) + start.toDuration
        }
      val monotonic: IO[FiniteDuration] = realTime
      override def toString = s"AdvanceByClock(start=$start, interval=$interval)"
    }

  def sequence(start: Instant, more: Instant*): Clock[IO] =
    val times = start :: more.toList
    new Clock[IO] {
      val counter = Ref.unsafe[IO, Int](0)
      val applicative: Applicative[IO] = Applicative[IO]
      val realTime: IO[FiniteDuration] =
        counter.getAndUpdate(_ + 1).map(n => times(n).toDuration)
      val monotonic: IO[FiniteDuration] = realTime
      override def toString = s"SequenceClock(start=$start, more=$more)"
    }
