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

package io.renku.solr.client

import java.util.UUID

import scala.concurrent.duration.*

import cats.data.NonEmptyList
import cats.effect.IO

import io.bullet.borer.derivation.MapBasedCodecs
import io.bullet.borer.derivation.key
import io.bullet.borer.{Decoder, Encoder, Reader}
import io.renku.search.GeneratorSyntax.*
import io.renku.solr.client.SolrClientSpec.{Course, Room}
import io.renku.solr.client.facet.{Facet, Facets}
import io.renku.solr.client.schema.*
import io.renku.solr.client.util.SolrClientBaseSuite
import munit.ScalaCheckEffectSuite
import org.scalacheck.Gen
import org.scalacheck.effect.PropF

class SolrClientSpec extends SolrClientBaseSuite with ScalaCheckEffectSuite:
  test("optimistic locking: fail if exists"):
    withSolrClient().use { client =>
      val c0 = Course("c1", "fp in scala", DocVersion.NotExists)
      for {
        _ <- client.deleteIds(NonEmptyList.of(c0.id))
        r0 <- client.upsert(Seq(c0))
        _ = assert(r0.isSuccess, clue = "Expected successful insert")

        rs <- client.findById[Course](c0.id)
        fetched = rs.responseBody.docs.head
        _ = assert(
          fetched.version.asLong > 0,
          clue = "stored entity version must be > 0"
        )
        _ = assert(
          fetched.copy(version = c0.version) == c0,
          clue = "stored entity not as expected"
        )

        r1 <- client.upsert(Seq(c0))
        _ = assertEquals(
          r1,
          UpsertResponse.VersionConflict,
          clue = "Expected VersionConflict"
        )
      } yield ()
    }

  test("use schema for inserting and querying"):
    val cmds = Seq(
      SchemaCommand.Add(FieldType.text(TypeName("roomText"), Analyzer.classic)),
      SchemaCommand.Add(FieldType.int(TypeName("roomInt"))),
      SchemaCommand.Add(Field(FieldName("roomName"), TypeName("roomText"))),
      SchemaCommand.Add(Field(FieldName("roomDescription"), TypeName("roomText"))),
      SchemaCommand.Add(Field(FieldName("roomSeats"), TypeName("roomInt")))
    )

    withSolrClient().use { client =>
      val room = Room(UUID.randomUUID().toString, "meeting room", "room for meetings", 56)
      for {
        _ <- truncateAll(client)(
          List("roomName", "roomDescription", "roomSeats").map(FieldName.apply),
          List("roomText", "roomInt").map(TypeName.apply)
        )
        _ <- client.modifySchema(cmds)
        _ <- client.upsert[Room](Seq(room))
        qr <- client.query[Room](QueryData(QueryString("_type_s:Room")))
        _ = qr.responseBody.docs contains room
        ir <- client.findById[Room](room.id)
        _ = ir.responseBody.docs contains room
      } yield ()
    }

  test("correct facet queries"):
    val decoder: Decoder[Unit] = new Decoder {
      def read(r: Reader): Unit =
        r.skipElement()
        ()
    }
    PropF.forAllF(SolrClientGenerator.facets) { facets =>
      val q = QueryData(QueryString("*:*")).withFacet(facets)
      withSolrClient().use { client =>
        client.query(q)(using decoder).void
      }
    }

  test("decoding facet response"):
    val rooms = Gen.listOfN(15, Room.gen).sample.get
    val facets =
      Facets(Facet.Terms(FieldName("by_name"), FieldName("roomName"), limit = Some(6)))
    withSolrClient().use { client =>
      for {
        _ <- client.delete(QueryString("_type_s:Room"))
        _ <- client.upsert(rooms)
        r <- client.query[Room](QueryData(QueryString("_type_s:Room")).withFacet(facets))
        _ = assert(r.facetResponse.nonEmpty)
        _ = assertEquals(r.facetResponse.get.count, 15)
        _ = assertEquals(
          r.facetResponse.get.buckets(FieldName("by_name")).buckets.size,
          6
        )
      } yield ()
    }

  test("delete by id"):
    withSolrClient().use { client =>
      for {
        id <- IO(Gen.uuid.generateOne).map(_.toString)
        _ <- client.delete(QueryString("_type_s:Person"))
        _ <- client.upsert(Seq(SolrClientSpec.Person(id, "John")))
        r <- client.query[SolrClientSpec.Person](QueryData(QueryString(s"id:$id")))
        p = r.responseBody.docs.head
        _ = assertEquals(p.id, id)
        _ <- client.deleteIds(NonEmptyList.of(id))
        _ <- IO.sleep(10.millis)
        r2 <- client.query[SolrClientSpec.Person](QueryData(QueryString(s"id:$id")))
        _ = assert(r2.responseBody.docs.isEmpty)
      } yield ()
    }

object SolrClientSpec:
  case class Room(id: String, roomName: String, roomDescription: String, roomSeats: Int)
  object Room:
    val gen: Gen[Room] = for {
      id <- Gen.uuid.map(_.toString)
      name <- Gen
        .choose(4, 12)
        .flatMap(n => Gen.listOfN(n, Gen.alphaChar))
        .map(_.mkString)
      descr = s"Room description for $name"
      seats <- Gen.choose(15, 350)
    } yield Room(id, name, descr, seats)

    given Decoder[Room] = MapBasedCodecs.deriveDecoder
    given Encoder[Room] = EncoderSupport.deriveWithDiscriminator[Room]("_type_s")

  case class Person(id: String, name_s: String)
  object Person:
    given Decoder[Person] = MapBasedCodecs.deriveDecoder
    given Encoder[Person] = EncoderSupport.deriveWithDiscriminator[Person]("_type_s")

  case class Course(
      id: String,
      name_s: String,
      @key("_version_") version: DocVersion = DocVersion.NotExists
  )
  object Course:
    given Decoder[Course] = MapBasedCodecs.deriveDecoder
    given Encoder[Course] = EncoderSupport.deriveWithDiscriminator[Course]("_type_s")
