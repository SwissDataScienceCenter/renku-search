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

import cats.effect.{IO, Resource}
import cats.syntax.all.*
import fs2.Stream

import io.renku.queue.client.{QueueClient, QueueSuite}
import io.renku.redis.client.QueueName
import io.renku.search.config.QueuesConfig
import io.renku.search.model.{EntityType, Id, Namespace}
import io.renku.search.provision.handler.PipelineSteps
import io.renku.search.solr.client.{SearchSolrClient, SearchSolrSuite}
import io.renku.search.solr.documents.{Group as GroupDocument, User as UserDocument, *}
import io.renku.search.solr.query.SolrToken
import io.renku.solr.client.{QueryData, QueryString}
import munit.CatsEffectSuite

trait ProvisioningSuite extends CatsEffectSuite with SearchSolrSuite with QueueSuite:
  val queueConfig: QueuesConfig = QueuesConfig(
    projectCreated = QueueName("projectCreated"),
    projectUpdated = QueueName("projectUpdated"),
    projectRemoved = QueueName("projectRemoved"),
    projectAuthorizationAdded = QueueName("projectAuthorizationAdded"),
    projectAuthorizationUpdated = QueueName("projectAuthorizationUpdated"),
    projectAuthorizationRemoved = QueueName("projectAuthorizationRemoved"),
    userAdded = QueueName("userAdded"),
    userUpdated = QueueName("userUpdated"),
    userRemoved = QueueName("userRemoved"),
    groupAdded = QueueName("groupAdded"),
    groupUpdated = QueueName("groupUpdated"),
    groupRemoved = QueueName("groupRemoved"),
    groupMemberAdded = QueueName("groupMemberAdded"),
    groupMemberUpdated = QueueName("groupMemberUpdated"),
    groupMemberRemoved = QueueName("groupMemberRemoved")
  )

  val testServicesR: Resource[IO, TestServices] =
    for
      solrClient <- searchSolrR
      queue <- queueClientR
      steps = PipelineSteps[IO](
        solrClient,
        Stream[IO, QueueClient[IO]](queue),
        inChunkSize = 1
      )
      handlers = MessageHandlers[IO](steps, queueConfig)
    yield TestServices(handlers, queue, solrClient)

  val testServices = ResourceSuiteLocalFixture("test-services", testServicesR)

  override def munitFixtures = List(solrServer, redisServer, testServices)

  def loadProjectsByNs(solrClient: SearchSolrClient[IO])(
      ns: Namespace
  ): IO[List[EntityDocument]] =
    val qd = QueryData(
      QueryString(
        List(
          SolrToken.namespaceIs(ns),
          SolrToken.entityTypeIs(EntityType.Project)
        ).foldAnd.value
      )
    )
    solrClient.queryAll[EntityDocument](qd).compile.toList

  private def getNamespace(doc: SolrDocument): Option[Namespace] = doc match
    case u: UserDocument                => u.namespace
    case g: GroupDocument               => g.namespace.some
    case g: PartialEntityDocument.Group => g.namespace
    case _                              => None

  def loadProjectPartialOrEntity(
      solrClient: SearchSolrClient[IO],
      id: Id
  ): IO[Set[SolrDocument]] =
    loadPartialOrEntity(solrClient, EntityType.Project, id)

  def loadGroupPartialOrEntity(
      solrClient: SearchSolrClient[IO],
      id: Id
  ): IO[Set[SolrDocument]] =
    loadPartialOrEntity(solrClient, EntityType.Group, id)

  def loadPartialOrEntity(
      solrClient: SearchSolrClient[IO],
      entityType: EntityType,
      id: Id
  ): IO[Set[SolrDocument]] =
    (
      solrClient.findById[EntityDocument](
        CompoundId.entity(id, entityType.some)
      ),
      solrClient.findById[PartialEntityDocument](
        CompoundId.partial(id, entityType.some)
      )
    ).mapN((a, b) => a.toSet ++ b.toSet)
