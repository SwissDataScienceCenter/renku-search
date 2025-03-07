package io.renku.search.provision.user

import cats.effect.IO
import cats.syntax.all.*

import io.renku.events.EventsGenerators
import io.renku.events.{v1, v2}
import io.renku.search.GeneratorSyntax.*
import io.renku.search.events.*
import io.renku.search.provision.ProvisioningSuite
import io.renku.search.provision.events.syntax.*
import io.renku.search.solr.documents.{CompoundId, EntityDocument}
import io.renku.solr.client.DocVersion
import org.scalacheck.Gen

class UserUpdatedProvisioningSpec extends ProvisioningSuite:
  (firstNameUpdate :: lastNameUpdate :: emailUpdate :: noUpdate :: Nil).foreach {
    case TestCase(name, updateF) =>
      val userAdded = EventsGenerators.userAddedGen(prefix = "user-update").generateOne
      test(s"can fetch events, decode them, and update in Solr in case of $name"):
        for
          services <- IO(testServices())
          handler = services.syncHandler(queueConfig.userUpdated)
          queueClient = services.queueClient
          solrClient = services.searchClient

          orig = userAdded.toModel(DocVersion.Off)
          _ <- solrClient.upsertSuccess(Seq(orig.widen))

          userUpdated = updateF(userAdded)
          _ <- queueClient.enqueue(
            queueConfig.userUpdated,
            EventsGenerators.eventMessageGen(Gen.const(userUpdated)).generateOne
          )

          result <- handler.create.take(1).compile.lastOrError
          _ = assert(result.asUpsert.exists(_.isSuccess))

          found <- solrClient
            .findById[EntityDocument](
              CompoundId.userEntity(userAdded.id)
            )

          _ = assertEquals(
            found.get.setVersion(DocVersion.Off),
            userUpdated.toModel(orig).setVersion(DocVersion.Off)
          )
        yield ()
  }

  private case class TestCase(name: String, f: UserAdded => UserUpdated)
  private lazy val firstNameUpdate = TestCase(
    "firstName update",
    {
      case UserAdded.V1(ua) =>
        UserUpdated.V1(
          v1.UserUpdated(
            ua.id,
            EventsGenerators.stringGen(max = 5).generateOne.some,
            ua.lastName,
            ua.email
          )
        )
      case UserAdded.V2(ua) =>
        UserUpdated.V2(
          v2.UserUpdated(
            ua.id,
            EventsGenerators.stringGen(max = 5).generateOne.some,
            ua.lastName,
            ua.email,
            ua.namespace
          )
        )
    }
  )
  private lazy val lastNameUpdate = TestCase(
    "lastName update",
    {
      case UserAdded.V1(ua) =>
        UserUpdated.V1(
          v1.UserUpdated(
            ua.id,
            ua.firstName,
            EventsGenerators.stringGen(max = 5).generateOne.some,
            ua.email
          )
        )
      case UserAdded.V2(ua) =>
        UserUpdated.V2(
          v2.UserUpdated(
            ua.id,
            ua.firstName,
            EventsGenerators.stringGen(max = 5).generateOne.some,
            ua.email,
            ua.namespace
          )
        )
    }
  )
  private lazy val emailUpdate = TestCase(
    "email update",
    {
      case UserAdded.V1(ua) =>
        UserUpdated.V1(
          v1.UserUpdated(
            ua.id,
            ua.firstName,
            ua.lastName,
            EventsGenerators.stringGen(max = 5).map(v => s"v@host.com").generateOne.some
          )
        )
      case UserAdded.V2(ua) =>
        UserUpdated.V2(
          v2.UserUpdated(
            ua.id,
            ua.firstName,
            ua.lastName,
            EventsGenerators.stringGen(max = 5).map(v => s"v@host.com").generateOne.some,
            ua.namespace
          )
        )
    }
  )
  private lazy val noUpdate = TestCase(
    "no update",
    {
      case UserAdded.V1(ua) =>
        UserUpdated.V1(v1.UserUpdated(ua.id, ua.firstName, ua.lastName, ua.email))
      case UserAdded.V2(ua) =>
        UserUpdated.V2(
          v2.UserUpdated(ua.id, ua.firstName, ua.lastName, ua.email, ua.namespace)
        )
    }
  )
