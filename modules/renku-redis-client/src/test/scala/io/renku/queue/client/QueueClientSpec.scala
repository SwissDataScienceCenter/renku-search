package io.renku.queue.client

import cats.data.NonEmptyList
import cats.effect.IO
import fs2.concurrent.SignallingRef

import io.renku.events.EventsGenerators
import io.renku.redis.client.RedisClientGenerators
import io.renku.search.GeneratorSyntax.*
import io.renku.search.events.*
import io.renku.search.events.EventMessage
import munit.AnyFixture
import munit.CatsEffectSuite

class QueueClientSpec extends CatsEffectSuite with QueueSuite:
  override def munitFixtures = List(redisServer, queueClient)

  test("can enqueue and dequeue project-member-add events"):
    val qname = RedisClientGenerators.queueNameGen.generateOne
    val qs = NonEmptyList.of(qname)
    val msg = EventsGenerators
      .eventMessageGen(EventsGenerators.projectMemberAddedGen)
      .generateOne
    for
      queue <- IO(queueClient())
      msgId <- queue.enqueue(qname, msg)
      res <- queue
        .acquireMessageStream[ProjectMemberAdded](qs, 1, None)
        .take(1)
        .compile
        .toList
      _ = assertEquals(res.head, msg.copy(id = msgId))
    yield ()

  test("can enqueue and dequeue project-created events"):
    val queue = RedisClientGenerators.queueNameGen.generateOne
    val qs = NonEmptyList.of(queue)
    for
      queueClient <- IO(queueClient())
      dequeued <- SignallingRef.of[IO, List[EventMessage[ProjectCreated]]](Nil)

      message0 = EventsGenerators
        .eventMessageGen(EventsGenerators.projectCreatedGen("test"))
        .generateOne
      message1Id <- queueClient.enqueue(queue, message0)
      message1 = message0.copy(id = message1Id)

      streamingProcFiber <- queueClient
        .acquireMessageStream[ProjectCreated](qs, chunkSize = 1, maybeOffset = None)
        .evalMap(event => dequeued.update(event :: _))
        .compile
        .drain
        .start
      _ <- dequeued.waitUntil(_.contains(message1))

      _ <- streamingProcFiber.cancel
    yield ()
