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

package io.renku.search.provision

import cats.effect.{Clock, IO, Resource}
import cats.syntax.all.*
import fs2.Stream
import fs2.concurrent.SignallingRef
import io.renku.avro.codec.AvroIO
import io.renku.avro.codec.encoders.all.given
import io.renku.messages.ProjectCreated
import io.renku.redis.client.RedisClientGenerators
import io.renku.redis.client.RedisClientGenerators.*
import io.renku.redis.client.util.RedisSpec
import io.renku.search.solr.client.SearchSolrSpec
import io.renku.search.solr.documents.Project
import munit.CatsEffectSuite
import scribe.Scribe

import java.time.temporal.ChronoUnit
import scala.concurrent.duration.*

class SearchProvisionerSpec extends CatsEffectSuite with RedisSpec with SearchSolrSpec:

  private given Scribe[IO] = scribe.cats[IO]
  private val avro = AvroIO(ProjectCreated.SCHEMA$)

  test("can fetch events and send them to Solr"):
    val queue = RedisClientGenerators.queueNameGen.generateOne

    (withRedisClient.asQueueClient() >>= withSearchSolrClient().tupleLeft)
      .use { case (queueClient, solrClient) =>
        val provisioner = new SearchProvisionerImpl(queue, queueClient, solrClient)
        for
          solrDocs <- SignallingRef.of[IO, Set[Project]](Set.empty)

          provisioningFiber <- provisioner.provisionSolr.start

          message1 <- generateProjectCreated("project", "description", Some("myself"))
          _ <- queueClient.enqueue(queue, avro.write[ProjectCreated](Seq(message1)))

          docsCollectorFiber <-
            Stream
              .awakeEvery[IO](500 millis)
              .evalMap(_ => solrClient.findProjects("*"))
              .flatMap(Stream.emits(_))
              .evalTap(IO.println)
              .evalMap(d => solrDocs.update(_ + d))
              .compile
              .drain
              .start

          _ <- solrDocs.waitUntil(_ contains toSolrDocument(message1))

          _ <- provisioningFiber.cancel
          _ <- docsCollectorFiber.cancel
        yield ()
      }

  private def generateProjectCreated(
      name: String,
      description: String,
      owner: Option[String]
  ): IO[ProjectCreated] =
    for
      now <- Clock[IO].realTimeInstant.map(_.truncatedTo(ChronoUnit.MILLIS))
      uuid <- IO.randomUUID
    yield ProjectCreated(uuid.toString, name, description, owner, now)

  private def toSolrDocument(created: ProjectCreated): Project =
    Project(created.id, created.name, created.description)

  override def munitFixtures: Seq[Fixture[_]] =
    List(withRedisClient, withSearchSolrClient)
