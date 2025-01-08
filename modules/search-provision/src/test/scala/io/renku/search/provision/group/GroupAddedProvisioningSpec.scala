package io.renku.search.provision.group

import cats.data.NonEmptyList
import cats.effect.IO

import io.renku.events.EventsGenerators
import io.renku.search.GeneratorSyntax.*
import io.renku.search.events.{GroupAdded, GroupUpdated}
import io.renku.search.model.ModelGenerators
import io.renku.search.model.{Name, Namespace}
import io.renku.search.provision.ProvisioningSuite
import io.renku.search.provision.events.syntax.*
import io.renku.search.solr.documents.{Group as GroupDocument, *}
import io.renku.solr.client.DocVersion
import org.scalacheck.Gen

class GroupAddedProvisioningSpec extends ProvisioningSuite:

  test("merge partial groups"):
    for
      services <- IO(testServices())
      handler = services.syncHandler(queueConfig.dataServiceAllEvents)
      queueClient = services.queueClient
      solrClient = services.searchClient

      id <- IO(ModelGenerators.idGen.generateOne)
      _ <- solrClient.deleteIds(NonEmptyList.of(id))
      add1 <- queueClient.enqueue(
        queueConfig.dataServiceAllEvents,
        EventsGenerators
          .eventMessageGen(
            Gen.const(GroupUpdated(id, Name("SDSC"), Namespace("sdsc-namespace"), None))
          )
          .generateOne
      )
      _ <- handler.create.map(_.asUpsert).unNone.take(1).compile.toList
      pdoc <- solrClient.findById[PartialEntityDocument](CompoundId.groupPartial(id))
      _ = assert(pdoc.isDefined, "no (partial) document found")
      _ = assert(pdoc.get.isInstanceOf[PartialEntityDocument.Group])

      add2 <- queueClient.enqueue(
        queueConfig.dataServiceAllEvents,
        EventsGenerators
          .eventMessageGen(
            Gen.const(
              GroupAdded(id, Name("Renku"), Namespace("sdsc-namespace"), None)
            )
          )
          .generateOne
      )
      results <- handler.create.map(_.asUpsert).unNone.take(1).compile.toList

      _ = assert(results.nonEmpty && results.forall(_.isSuccess))
      doc <- solrClient.findById[EntityDocument](CompoundId.groupEntity(id))
      _ = assert(doc.isDefined, "group not found")
      group = doc.get.asInstanceOf[GroupDocument]
      _ = assertEquals(group.name, Name("SDSC"))
    yield ()

  test("overwrite data for duplicate events"):
    for
      services <- IO(testServices())
      handler = services.syncHandler(queueConfig.groupAdded)
      queueClient = services.queueClient
      solrClient = services.searchClient

      id <- IO(ModelGenerators.idGen.generateOne)
      _ <- solrClient.deleteIds(NonEmptyList.of(id))
      add1 <- queueClient.enqueue(
        queueConfig.groupAdded,
        EventsGenerators
          .eventMessageGen(
            Gen.const(GroupAdded(id, Name("SDSC"), Namespace("sdsc-namespace"), None))
          )
          .generateOne
      )
      add2 <- queueClient.enqueue(
        queueConfig.groupAdded,
        EventsGenerators
          .eventMessageGen(
            Gen.const(
              GroupAdded(id, Name("Renku"), Namespace("sdsc-namespace"), None)
            )
          )
          .generateOne
      )
      results <- handler.create
        .take(2)
        .map(_.asUpsert)
        .unNone
        .compile
        .toList

      _ = assert(results.nonEmpty && results.forall(_.isSuccess))
      doc <- solrClient.findById[EntityDocument](CompoundId.groupEntity(id))
      _ = assert(doc.isDefined, "group not found")
      group = doc.get.asInstanceOf[GroupDocument]
      _ = assertEquals(group.name, Name("Renku"))
    yield ()

  test("can fetch events, decode them, and send them to Solr"):
    val groupAdded = EventsGenerators.groupAddedGen(prefix = "group-added").generateOne
    for
      services <- IO(testServices())
      handler = services.syncHandler(queueConfig.groupAdded)
      queueClient = services.queueClient
      solrClient = services.searchClient

      _ <- queueClient.enqueue(
        queueConfig.groupAdded,
        EventsGenerators.eventMessageGen(Gen.const(groupAdded)).generateOne
      )

      result <- handler.create
        .take(10)
        .map(_.asUpsert)
        .unNone
        .find(_.isSuccess)
        .compile
        .lastOrError

      _ = assert(result.isSuccess)

      doc <- solrClient.findById[EntityDocument](
        CompoundId.groupEntity(groupAdded.id)
      )
      _ = assert(doc.isDefined)
      _ = assertEquals(
        doc.get.setVersion(DocVersion.Off),
        groupAdded.fold(_.toModel(DocVersion.Off))
      )
    yield ()
