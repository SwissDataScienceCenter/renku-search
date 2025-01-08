package io.renku.search.provision.user

import cats.data.NonEmptyList
import cats.effect.IO
import cats.syntax.all.*

import io.renku.events.EventsGenerators
import io.renku.search.GeneratorSyntax.*
import io.renku.search.events.UserAdded
import io.renku.search.model.*
import io.renku.search.provision.ProvisioningSuite
import io.renku.search.provision.events.syntax.*
import io.renku.search.solr.documents.{CompoundId, EntityDocument, User as UserDocument}
import io.renku.solr.client.DocVersion
import org.scalacheck.Gen

class UserAddedProvisioningSpec extends ProvisioningSuite:
  test("overwrite data for duplicate events"):
    for
      services <- IO(testServices())
      handler = services.syncHandler(queueConfig.userAdded)
      queueClient = services.queueClient
      solrClient = services.searchClient

      id <- IO(ModelGenerators.idGen.generateOne)
      _ <- solrClient.deleteIds(NonEmptyList.of(id))
      results1 <- handler.processEvent(
        EventsGenerators
          .eventMessageGen(
            EventsGenerators
              .userAddedGen("ua-", Gen.const(FirstName("john1").some))
              .map(_.withId(id))
          )
          .generateOne
      )

      results2 <- handler.processEvent(
        EventsGenerators
          .eventMessageGen(
            EventsGenerators
              .userAddedGen("ua-", Gen.const(FirstName("john2").some))
              .map(_.withId(id))
          )
          .generateOne
      )
      results = List(results1.asUpsert, results2.asUpsert).flatten

      _ = assert(results.nonEmpty && results.forall(_.isSuccess))
      doc <- solrClient.findById[EntityDocument](CompoundId.userEntity(id))
      _ = assert(doc.isDefined, "user not found")
      user = doc.get.asInstanceOf[UserDocument]
      _ = assertEquals(user.firstName, Some(FirstName("john2")))
    yield ()

  test("can fetch events, decode them, and send them to Solr"):
    val userAdded = EventsGenerators.userAddedGen(prefix = "user-added").generateOne
    for
      services <- IO(testServices())
      handler = services.syncHandler(queueConfig.userAdded)
      queueClient = services.queueClient
      solrClient = services.searchClient

      _ <- queueClient.enqueue(
        queueConfig.userAdded,
        EventsGenerators.eventMessageGen(Gen.const(userAdded)).generateOne
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
        CompoundId.userEntity(userAdded.id)
      )
      _ = assert(doc.isDefined)
      _ = assertEquals(
        doc.get.setVersion(DocVersion.Off),
        userAdded.toModel(DocVersion.Off)
      )
    yield ()
