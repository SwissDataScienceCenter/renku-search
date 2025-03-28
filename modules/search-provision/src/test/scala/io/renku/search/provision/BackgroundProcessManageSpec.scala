package io.renku.search.provision

import scala.concurrent.duration.*

import cats.effect.*
import fs2.Stream

import io.renku.search.LoggingConfigure
import io.renku.search.provision.BackgroundProcessManageSpec.Key
import munit.CatsEffectSuite

class BackgroundProcessManageSpec extends CatsEffectSuite with LoggingConfigure:

  def makeInfiniteTask(effect: IO[Unit], pause: FiniteDuration = 20.millis) = Stream
    .repeatEval(effect)
    .interleave(Stream.sleep[IO](pause).repeat)
    .compile
    .drain

  val manager = BackgroundProcessManage[IO](10.millis)

  test("register tasks, start and cancel"):
    manager.use { m =>
      for
        counter <- Ref[IO].of(0)
        task = makeInfiniteTask(counter.update(_ + 1))
        _ <- m.register(Key.Count, task)
        _ <- m.startAll
        ps <- m.currentProcesses
        _ = assertEquals(ps, Set(Key.Count.widen))

        _ <- IO.sleep(50.millis)
        _ <- m.cancelProcesses(_ => true)
        ps2 <- m.currentProcesses
        _ = assert(ps2.isEmpty)
        _ <- IO.sleep(50.millis)
        n <- counter.get
        _ = assert(n >= 1 && n <= 3, s"$n is not between 1 and 3 (inclusive)")
      yield ()
    }

  test("remove process when done"):
    manager.use { m =>
      for
        _ <- m.register(Key.Nothing, IO.unit)
        _ <- m.startAll
        _ <- IO.sleep(10.millis)
        ps <- m.currentProcesses
        _ = assert(ps.isEmpty)
      yield ()
    }

  test("restart on error"):
    manager.use { m =>
      for
        counter <- Ref[IO].of(0)
        task = makeInfiniteTask(counter.updateAndGet(_ + 1).flatMap { n =>
          if (n % 3 == 0) IO.raiseError(new Exception("boom")) else IO.unit
        })
        _ <- m.register(Key.Errors, task)
        _ <- m.startAll
        _ <- IO.sleep(100.millis)
        _ <- m.cancelProcesses(_ => true)
        n <- counter.get
        _ = assert(n > 3, s"$n is not greater than 3")
      yield ()
    }

object BackgroundProcessManageSpec:

  enum Key extends BackgroundProcessManage.TaskName:
    case Count
    case Errors
    case Nothing

    def widen: BackgroundProcessManage.TaskName = this
