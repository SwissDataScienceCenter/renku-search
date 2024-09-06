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

package io.renku.solr.client.util

import java.util.UUID

import cats.effect.*
import cats.syntax.all.*

import io.bullet.borer.Decoder
import io.bullet.borer.Encoder
import io.bullet.borer.derivation.MapBasedCodecs
import io.bullet.borer.derivation.key
import io.renku.solr.client.DocVersion
import io.renku.solr.client.SolrClient
import io.renku.solr.client.util.DocumentLockResourceSpec.{Lock, SimpleLock}
import munit.CatsEffectSuite

class DocumentLockResourceSpec
    extends CatsEffectSuite
    with SolrClientBaseSuite
    with io.renku.search.Threading:
  override def munitFixtures: Seq[munit.AnyFixture[?]] =
    List(solrServer, solrClient)

  def makeSimpleLock(id: String, client: SolrClient[IO]) =
    DocumentLockResource[IO, SimpleLock](client).make(id)

  def makeLock(id: String, client: SolrClient[IO]) =
    DocumentLockResource[IO, Lock](client).make(id)

  def makeId = IO(UUID.randomUUID().toString())

  test("obtain lock if document doesn't exist"):
    for
      client <- IO(solrClient())
      id <- makeId
      lock1 = makeSimpleLock(id, client)
      _ <- lock1.use(a => IO(a.isDefined)).assertEquals(true)
      // make sure it is removed
      _ <- client.assertDocGone[SimpleLock](id)

      lock2 = makeLock(id, client)
      _ <- lock2.use(a => IO(a.isDefined)).assertEquals(true)
      // document must exist in free state
      _ <- client.assertDocExists[Lock](id, _.occupied == false)
    yield ()

  test("obtain lock if document exists in free state"):
    for
      client <- IO(solrClient())
      id <- makeId
      _ <- client.upsertSuccess(Seq(Lock(id, false, DocVersion.NotExists)))

      lock = makeLock(id, client)
      _ <- lock.use(a => IO(a.isDefined)).assertEquals(true)
      // document must exists unchanged
      _ <- client.assertDocExists[Lock](id, _.occupied == false)
    yield ()

  test("release if action fails"):
    for
      client <- IO(solrClient())
      id <- makeId
      lock = makeSimpleLock(id, client)

      r <- lock.use(a => IO.raiseError(new Exception("fail")).void).attempt
      _ = assert(r.isLeft)
      _ <- client.assertDocGone[SimpleLock](id)

      lock2 = makeLock(id, client)
      r2 <- lock2.use(_ => IO.raiseError(new Exception("fail")).void).attempt
      _ = assert(r2.isLeft)
      _ <- client.assertDocExists[Lock](id, _.occupied == false)
    yield ()

  test("don't lock if document exists in non-free state"):
    for
      client <- IO(solrClient())
      id <- makeId
      doc = Lock(id, false, DocVersion.NotExists)
      _ <- client.upsertSuccess(Seq(doc))

      l1 = makeSimpleLock(id, client)
      _ <- l1.use(a => IO(a.isDefined)).assertEquals(false)
      _ <- client.assertDocExists[Lock](id, _ => true)

      _ <- client.upsertSuccess(Seq(doc.setOccupied.noVersioning))
      _ <- l1.use(a => IO(a.isDefined)).assertEquals(false)
      _ <- client.assertDocExists[Lock](id, _.occupied == true)

      l2 = makeLock(id, client)
      _ <- l2.use(a => IO(a.isDefined)).assertEquals(false)
      _ <- client.assertDocExists[Lock](id, _.occupied == true)
    yield ()

  test("document exists while resource is in use"):
    for
      client <- IO(solrClient())
      id <- makeId

      l1 = makeSimpleLock(id, client)
      l2 = makeLock(id, client)

      _ <- l1.use { d =>
        assert(d.isDefined)
        client.assertDocExists[SimpleLock](id, _ => true)
      }
      _ <- l2.use { d =>
        assert(d.isDefined)
        client.assertDocExists[Lock](id, _ => true)
      }
    yield ()

  test("run nested locks"):
    for
      client <- IO(solrClient())
      id <- makeId
      lock = DocumentLockResource.create(client)(id)

      _ <- lock.use { b1 =>
        assert(b1, "first lock didn't succeed")
        lock.use { b2 =>
          IO.pure(assert(!b2, "nested lock did succeed!"))
        }
        // second lock must not remove anything
        client.assertDocExists[SimpleLock](id, _ => true)
      }
      _ <- client.assertDocGone[SimpleLock](id)
    yield ()

  override def munitFlakyOK: Boolean = true

  test("try running in parallel".flaky):
    for
      client <- IO(solrClient())
      id <- makeId
      lock = DocumentLockResource.create(client)(id)
      task = lock.use(IO.pure)
      r <- runParallel(task, task)
      _ = assertEquals(r.toSet, Set(false, true), s"Result: $r")
    yield ()

  extension (self: SolrClient[IO])
    def assertDocGone[A: Decoder](id: String) =
      self.findById[A](id).map(_.responseBody.numFound).assertEquals(0L)

    def assertDocExists[A: Decoder](id: String, check: A => Boolean) =
      self
        .findById[A](id)
        .map(_.responseBody.docs.head)
        .assert(check)

object DocumentLockResourceSpec:
  final case class SimpleLock(id: String, @key("_version_") version: DocVersion)
  object SimpleLock:
    given Encoder[SimpleLock] = MapBasedCodecs.deriveEncoder
    given Decoder[SimpleLock] = MapBasedCodecs.deriveDecoder

    given LockDocument[IO, SimpleLock] =
      LockDocument.whenExists(SimpleLock(_, DocVersion.NotExists).pure[IO])

  final case class Lock(
      id: String,
      @key("occupied_b") occupied: Boolean,
      @key("_version_") version: DocVersion
  ):
    val notOccupied: Boolean = !occupied
    def setOccupied: Lock = copy(occupied = true)
    def setFree: Lock = copy(occupied = false)
    def noVersioning: Lock = copy(version = DocVersion.Off)

  object Lock:
    given Encoder[Lock] = MapBasedCodecs.deriveEncoder
    given Decoder[Lock] = MapBasedCodecs.deriveDecoder

    given LockDocument[IO, Lock] = LockDocument(!_.occupied, next, _.setFree.some)

    def next(in: Option[Lock], id: String): IO[Lock] =
      IO.pure(in.map(_.setOccupied).getOrElse(Lock(id, true, DocVersion.NotExists)))
