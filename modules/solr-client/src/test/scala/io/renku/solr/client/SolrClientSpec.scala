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
import io.renku.json.EncoderSupport
import io.renku.search.GeneratorSyntax.*
import io.renku.solr.client.SolrClientSpec.CourseMember
import io.renku.solr.client.SolrClientSpec.{Course, Room}
import io.renku.solr.client.facet.{Facet, Facets}
import io.renku.solr.client.schema.*
import io.renku.solr.client.util.SolrClientBaseSuite
import munit.CatsEffectSuite
import munit.ScalaCheckEffectSuite
import org.scalacheck.Gen
import org.scalacheck.effect.PropF

class SolrClientSpec
    extends CatsEffectSuite
    with SolrClientBaseSuite
    with ScalaCheckEffectSuite:

  override def munitFixtures: Seq[munit.AnyFixture[?]] =
    List(solrServer, solrClient)

  test("use cursor for pagination") {
    val courses = List.unfold(0) { n =>
      if (n > 10) None
      else Some((Course(f"c-$n%03d", s"fp in scala $n", DocVersion.NotExists), n + 1))
    }
    for
      client <- IO(solrClient())
      _ <- client.deleteIds(NonEmptyList.fromListUnsafe(courses.map(_.id)))
      r0 <- client.upsert(courses)
      _ = assert(r0.isSuccess, clue = "Expected successful insert")

      query = QueryData(QueryString("*:*"))
        .withCursor(CursorMark.Start, FieldName.id)
        .appendSort(FieldName("name_s"))
        .withLimit(4)

      r1 <- client.query[Course](query)
      _ = assert(r1.nextCursor.isDefined)
      _ = assertEquals(r1.responseBody.docs.map(_.id), courses.take(4).map(_.id))

      r2 <- client.query[Course](query.withCursor(r1.nextCursor.get))
      _ = assert(r2.nextCursor.isDefined)
      _ = assertEquals(r2.responseBody.docs.map(_.id), courses.drop(4).take(4).map(_.id))

      r3 <- client.query[Course](query.withCursor(r2.nextCursor.get))
      _ = assert(r3.nextCursor.isDefined)
      _ = assertEquals(r3.responseBody.docs.map(_.id), courses.drop(8).take(4).map(_.id))

      r4 <- client.query[Course](query.withCursor(r3.nextCursor.get))
      _ = assert(r4.nextCursor.isDefined)
      _ = assertEquals(r4.responseBody.docs, Nil)

      r5 <- client.query[Course](query.withCursor(r4.nextCursor.get))
      _ = assert(r5.nextCursor.isDefined)
      _ = assertEquals(r5.nextCursor, r4.nextCursor)
    yield ()
  }

  test("optimistic locking: fail if exists") {
    val c0 = Course("c1", "fp in scala", DocVersion.NotExists)
    for {
      client <- IO(solrClient())
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

  test("use schema for inserting and querying") {
    val cmds = Seq(
      SchemaCommand.Add(
        FieldType.text(TypeName("roomText")).withAnalyzer(Analyzer.classic)
      ),
      SchemaCommand.Add(FieldType.int(TypeName("roomInt"))),
      SchemaCommand.Add(Field(FieldName("roomName"), TypeName("roomText"))),
      SchemaCommand.Add(Field(FieldName("roomDescription"), TypeName("roomText"))),
      SchemaCommand.Add(Field(FieldName("roomSeats"), TypeName("roomInt")))
    )

    val room = Room(UUID.randomUUID().toString, "meeting room", "room for meetings", 56)
    for {
      client <- IO(solrClient())
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

  test("correct facet queries") {
    val decoder: Decoder[Unit] = new Decoder {
      def read(r: Reader): Unit =
        r.skipElement()
        ()
    }
    val client = solrClient()
    PropF.forAllF(SolrClientGenerator.facets) { facets =>
      val q = QueryData(QueryString("*:*")).withFacet(facets)
      client.query(q)(using decoder).void
    }
  }

  test("decoding facet response") {
    val rooms = Gen.listOfN(15, Room.gen).sample.get
    val facets =
      Facets(Facet.Terms(FieldName("by_name"), FieldName("roomName"), limit = Some(6)))

    for {
      client <- IO(solrClient())
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

  test("delete by id") {
    for {
      client <- IO(solrClient())
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

  test("create and delete core") {
    val name = Gen
      .choose(5, 12)
      .flatMap(n => Gen.listOfN(n, Gen.alphaChar))
      .map(_.mkString)
      .generateOne

    for
      client <- IO(solrClient())
      _ <- client.createCore(name)
      s1 <- client.getStatus
      _ = assert(s1.status.keySet.contains(name), s"core $name not available")
      id <- IO(Gen.uuid.generateOne).map(_.toString)
      _ <- client.upsert(Seq(SolrClientSpec.Person(id, "John")))
      _ <- client.query[SolrClientSpec.Person](QueryData(QueryString(s"id:$id")))
      _ <- client.deleteCore(name)
      s2 <- client.getStatus
      _ = assert(!s2.status.keySet.contains(name), s"core $name is not deleted")
    yield ()
  }

  test("Obtain schema"):
    for
      client <- IO(solrClient())
      schema <- client.getSchema
      _ = assert(schema.schema.fieldTypes.nonEmpty)
    yield ()

  test("use subqueries"):
    for
      client <- IO(solrClient())
      course = Course("course1", "TechCourse")
      courseMember = CourseMember("course-mem-1", "John Doe", "course1")
      _ <- client.upsert(Seq(course))
      _ <- client.upsert(Seq(courseMember))

      resNormal <- client.query[CourseMember](
        QueryData("_type_s:CourseMember", Seq.empty, 10, 0)
      )
      _ = assertEquals(
        resNormal.responseBody.docs.head.copy(version = DocVersion.NotExists),
        courseMember
      )

      resSub <- client.query[CourseMember](
        QueryData("_type_s:CourseMember", Seq.empty, 10, 0)
          .withFields(FieldName.all)
          .addSubQuery(
            FieldName("courseFull"),
            SubQuery(
              query = "{!terms f=id v=$row.course_id_s}",
              filter = "{!terms f=_type_s v=Course}",
              fields = Seq(FieldName.all),
              limit = 2
            )
          )
      )
      _ = assertEquals(
        resSub.responseBody.docs.head.courseFull.get.docs.head
          .copy(version = DocVersion.NotExists),
        course
      )
    yield ()

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

  final case class CourseMember(
      id: String,
      @key("name_s") name: String,
      @key("course_id_s") courseId: String,
      courseFull: Option[ResponseBody[Course]] = None,
      @key("_version_") version: DocVersion = DocVersion.NotExists
  )
  object CourseMember:
    given Decoder[CourseMember] = MapBasedCodecs.deriveDecoder
    given Encoder[CourseMember] =
      EncoderSupport.deriveWithDiscriminator[CourseMember]("_type_s")
