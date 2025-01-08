package io.renku.search.provision

import java.util.concurrent.TimeoutException

import scala.concurrent.duration.*

import cats.effect.IO
import cats.effect.kernel.Fiber
import fs2.Stream
import fs2.concurrent.SignallingRef

import io.renku.search.provision.BackgroundCollector.State

final class BackgroundCollector[D](
    task: IO[Set[D]],
    state: SignallingRef[IO, State[D]],
    every: FiniteDuration = 500.millis
):
  private val logger = scribe.cats.io

  def waitUntil(cond: Set[D] => Boolean, timeout: FiniteDuration = 15.seconds): IO[Unit] =
    val find = state.get.map(_.lastUpdate).flatMap { last =>
      val until = last.map(_ + timeout).getOrElse(timeout)
      state.waitUntil(s => cond(s.docs) || s.isTimeout(until))
    }
    val cancel = state.get.flatMap(_.cancel)
    val finish = state.get.flatMap { s =>
      if (cond(s.docs)) IO.unit
      else
        IO.raiseError(
          new TimeoutException(s"Timeout while waiting for condition on: ${s.docs}")
        )
    }
    find >> cancel >> finish

  def start: IO[Unit] =
    val periodicTask =
      Stream
        .awakeEvery[IO](every)
        .evalTap(time => state.update(_.updateAt(time)))
        .evalMap { time =>
          task.flatMap(d =>
            logger.trace(s"Periodic result: $d") >> state.update(_.add(d))
          )
        }
        .compile
        .drain
    periodicTask.start.flatMap { fiber =>
      state.update(_.withFiber(fiber))
    }

object BackgroundCollector:
  def apply[D](
      task: IO[Set[D]],
      every: FiniteDuration = 500.millis
  ): IO[BackgroundCollector[D]] =
    SignallingRef[IO].of(State[D]()).map { state =>
      new BackgroundCollector(task, state, every)
    }

  final case class State[D](
      docs: Set[D] = Set.empty[D],
      lastUpdate: Option[FiniteDuration] = None,
      fiber: Option[Fiber[IO, Throwable, Unit]] = None
  ):
    def add(doc: Set[D]): State[D] =
      copy(docs = docs ++ doc)

    def updateAt(fd: FiniteDuration): State[D] =
      copy(lastUpdate = Some(fd))

    def withFiber(fiber: Fiber[IO, Throwable, Unit]): State[D] =
      copy(fiber = Some(fiber))

    def isTimeout(max: FiniteDuration): Boolean =
      lastUpdate.getOrElse(Duration.Zero) >= max

    def cancel: IO[Unit] =
      fiber.map(_.cancel).getOrElse(IO.unit)
