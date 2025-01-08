package io.renku.events

import java.util.concurrent.atomic.AtomicLong

import cats.effect.*

import io.renku.search.events.MessageId

// Just for testing, generate redis compatible ids
object RedisIdGen:

  private val millis = new AtomicLong(System.currentTimeMillis())
  private val counter = new AtomicLong(0)

  def unsafeNextId: MessageId =
    val c = counter.getAndIncrement()
    val ms = System.currentTimeMillis()
    val curMs = millis.get
    if (curMs == ms) {
      MessageId(s"${ms}-${counter.getAndIncrement()}")
    } else {
      if (millis.compareAndSet(curMs, ms)) {
        counter.set(0)
        MessageId(s"${ms}-$c")
      } else
        unsafeNextId
    }

  def nextId: IO[MessageId] =
    IO(unsafeNextId)
