package io.renku.search

import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

import scala.util.Try

import cats.effect.*

import munit.*

trait Threading:
  self: CatsEffectSuite =>

  // using "real" threads to have higher chance of parallelism

  def runParallel[A](block: => IO[A], blockn: IO[A]*): IO[List[A]] =
    IO.blocking {
      val code = block +: blockn
      val latch = CountDownLatch(1)
      val result = new AtomicReference[List[A]](Nil)
      val done = new CountDownLatch(code.size)
      code.foreach { ioa =>
        val t = new Thread(new Runnable {
          def run() =
            latch.await()
            val ta = Try(ioa.unsafeRunSync())
            ta.fold(_ => (), a => result.updateAndGet(list => a :: list))
            done.countDown()
            ta.fold(throw _, _ => ())
            ()
        })
        t.setDaemon(true)
        t.start()
      }
      latch.countDown()
      done.await(munitIOTimeout.toMillis, TimeUnit.MILLISECONDS)
      result.get()
    }
