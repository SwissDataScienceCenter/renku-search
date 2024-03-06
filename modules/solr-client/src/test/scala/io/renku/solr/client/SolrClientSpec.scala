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
      for {
        _ <- client.modifySchema(cmds)
        _ <- client
          .insert[Room](Seq(Room("meeting room", "room for meetings", 56)))
        r <- client.query[Room](QueryData(QueryString("_type:Room")))
        _ <- IO.println(r)
      } yield ()
    }

  test("correct facet queries"):
    given Decoder[Unit] = new Decoder {
      def read(r: Reader): Unit =
        r.skipElement()
        ()
    }
    PropF.forAllF(SolrClientGenerator.facets) { facets =>
      val q = QueryData(QueryString("*:*")).withFacet(facets)
      withSolrClient().use { client =>
        client.query[Unit](q).void
      }
    }

object SolrClientSpec:
  case class Room(roomName: String, roomDescription: String, roomSeats: Int)
  object Room:
    given Decoder[Room] = deriveDecoder
    given Encoder[Room] = EncoderSupport.deriveWithDiscriminator[Room]
