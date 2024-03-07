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

import cats.effect.IO
import cats.syntax.all.*
import io.bullet.borer.derivation.MapBasedCodecs.deriveDecoder
import io.bullet.borer.{Decoder, Encoder}
import io.renku.solr.client.SolrClientSpec.Room
import io.renku.solr.client.schema.*
import io.renku.solr.client.util.{SolrSpec, SolrTruncate}
import munit.CatsEffectSuite
import munit.ScalaCheckEffectSuite
import org.scalacheck.effect.PropF
import io.bullet.borer.Reader
import org.scalacheck.Gen
import io.renku.solr.client.facet.{Facet, Facets}

class SolrClientSpec
    extends CatsEffectSuite
    with ScalaCheckEffectSuite
    with SolrSpec
    with SolrTruncate:

  test("use schema for inserting and querying"):
    val cmds = Seq(
      SchemaCommand.Add(FieldType.text(TypeName("roomText"), Analyzer.classic)),
      SchemaCommand.Add(FieldType.int(TypeName("roomInt"))),
      SchemaCommand.Add(Field(FieldName("roomName"), TypeName("roomText"))),
      SchemaCommand.Add(Field(FieldName("roomDescription"), TypeName("roomText"))),
      SchemaCommand.Add(Field(FieldName("roomSeats"), TypeName("roomInt")))
    )
    withSolrClient().use { client =>
      val rooms = Seq(Room("meeting room", "room for meetings", 56))
      for {
        _ <- truncateAll(client)(
          List("roomName", "roomDescription", "roomSeats").map(FieldName.apply),
          List("roomText", "roomInt").map(TypeName.apply)
        )
        _ <- client.modifySchema(cmds)
        _ <- client
          .insert[Room](rooms)
        r <- client.query[Room](QueryData(QueryString("_type:Room")))
        _ = assertEquals(r.responseBody.docs, rooms)
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
        _ <- client.delete(QueryString("*:*"))
        _ <- client.insert(rooms)
        r <- client.query[Room](QueryData(QueryString("*:*")).withFacet(facets))
        _ = assert(r.facetResponse.nonEmpty)
        _ = assertEquals(r.facetResponse.get.count, 15)
        _ = assertEquals(
          r.facetResponse.get.buckets(FieldName("by_name")).buckets.size,
          6
        )
      } yield ()
    }

object SolrClientSpec:
  case class Room(roomName: String, roomDescription: String, roomSeats: Int)
  object Room:
    val gen: Gen[Room] = for {
      name <- Gen
        .choose(4, 12)
        .flatMap(n => Gen.listOfN(n, Gen.alphaChar))
        .map(_.mkString)
      descr = s"Room description for $name"
      seats <- Gen.choose(15, 350)
    } yield Room(name, descr, seats)

    given Decoder[Room] = deriveDecoder
    given Encoder[Room] = EncoderSupport.deriveWithDiscriminator[Room]
