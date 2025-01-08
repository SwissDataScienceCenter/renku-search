package io.renku.search.provision

import scala.concurrent.duration.*

import cats.effect.{IO, Resource}
import cats.syntax.all.*
import fs2.Stream

import io.renku.queue.client.{QueueClient, QueueSuite}
import io.renku.redis.client.QueueName
import io.renku.search.config.QueuesConfig
import io.renku.search.model.{EntityType, Id, Namespace}
import io.renku.search.provision.handler.PipelineSteps
import io.renku.search.provision.reindex.ReIndexService
import io.renku.search.provision.reindex.ReprovisionService
import io.renku.search.sentry.Sentry
import io.renku.search.solr.client.{SearchSolrClient, SearchSolrSuite}
import io.renku.search.solr.documents.{Group as GroupDocument, User as UserDocument, *}
import io.renku.search.solr.query.SolrToken
import io.renku.solr.client.{QueryData, QueryString}
import munit.CatsEffectSuite

trait ProvisioningSuite extends CatsEffectSuite with SearchSolrSuite with QueueSuite:
  val queueConfig: QueuesConfig = ProvisioningSuite.queueConfig

  override def munitIOTimeout: Duration = Duration(1, "min")

  val testServicesR: Resource[IO, TestServices] =
    for
      solrClient <- searchSolrR
      queue <- queueClientR
      steps = PipelineSteps[IO](
        solrClient,
        Stream[IO, QueueClient[IO]](queue),
        inChunkSize = 1
      )
      bpm <- BackgroundProcessManage[IO](50.millis)
      reindex = ReIndexService[IO](
        bpm,
        Stream[IO, QueueClient[IO]](queue),
        solrClient,
        queueConfig
      )
      rps = ReprovisionService(reindex, solrClient.underlying)
      ctrl <- Resource.eval(SyncMessageHandler.Control[IO])
      handlers = MessageHandlers[IO](steps, rps, Sentry.noop[IO], queueConfig, ctrl)
    yield TestServices(steps, handlers, queue, solrClient, bpm, reindex)

  val testServices = ResourceSuiteLocalFixture("test-services", testServicesR)

  override def munitFixtures = List(solrServer, redisServer, testServices, redisClients)

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

  def waitForSolrDocs(
      services: TestServices,
      query: QueryData,
      until: List[EntityDocument] => Boolean,
      timeout: FiniteDuration = 30.seconds
  ): IO[List[EntityDocument]] =
    Stream
      .repeatEval(
        scribe.cats.io.trace(s"Searching $query…") >>
          services.searchClient
            .queryAll[EntityDocument](query)
            .compile
            .toList
      )
      .takeThrough(d => !until(d))
      .meteredStartImmediately(50.millis)
      .interruptWhen(
        IO.sleep(timeout)
          .as(Left(new Exception(s"Query timed out after $timeout: $query")))
      )
      .compile
      .lastOrError

object ProvisioningSuite:
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
    groupMemberRemoved = QueueName("groupMemberRemoved"),
    dataServiceAllEvents = QueueName("dataServiceAllEvents")
  )
