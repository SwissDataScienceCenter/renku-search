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

import io.bullet.borer.Decoder
import io.bullet.borer.Encoder
import io.bullet.borer.derivation.MapBasedCodecs
import io.bullet.borer.derivation.key
import io.renku.solr.client.SearchCaseInsensitiveSpec.TestData
import io.renku.solr.client.schema.*
import io.renku.solr.client.util.SolrClientBaseSuite
import munit.CatsEffectSuite

class SearchCaseInsensitiveSpec extends CatsEffectSuite with SolrClientBaseSuite:
  private val logger = scribe.cats.io

  override def munitFixtures: Seq[munit.AnyFixture[?]] =
    List(solrServer, solrClient)

  private val migrations = Seq(
    SchemaCommand.Add(
      FieldType.text(TypeName("my_text_field")).withAnalyzer(Analyzer.defaultSearch)
    ),
    SchemaCommand.Add(Field(FieldName("my_name"), TypeName("my_text_field")))
  )

  private def truncate(client: SolrClient[IO]): IO[Unit] =
    truncateQuery(client)(
      SearchCaseInsensitiveSpec.idQuery,
      Seq(FieldName("my_name")),
      Seq(TypeName("my_text_field"))
    )

  test("search case insensitive") {
    for {
      client <- IO(solrClient())
      _ <- truncate(client)
      _ <- client.modifySchema(migrations)
      _ <- client.upsert(TestData.sample)

      // find pogacar without this Č character
      r1 <- client.query[TestData](QueryString("my_name:pogacar"))
      _ = assertEquals(r1.responseBody.docs.head, TestData.get(11))
      // find pogi with that Č character
      r2 <- client.query[TestData](QueryString("my_name:POGAČAR"))
      _ = assertEquals(r2.responseBody.docs.head, TestData.get(11))
      // find with umlaut
      r3 <- client.query[TestData](QueryString("my_name:über"))
      _ = assertEquals(r3.responseBody.docs.head, TestData.get(31))
      // find without umlaut
      r4 <- client.query[TestData](QueryString("my_name:uber"))
      _ = assertEquals(r4.responseBody.docs.head, TestData.get(31))
    } yield ()
  }

object SearchCaseInsensitiveSpec:
  def idQuery: String = s"id:${getClass.getSimpleName}*"
  def id(num: Int): String = s"${getClass.getSimpleName}_$num"

  final case class TestData(id: String, @key("my_name") name: String)
  object TestData:
    val sample = Seq(
      TestData(id(1), "Eddy MERCKX"),
      TestData(id(2), "Alejandro VALVERDE"),
      TestData(id(3), "Sean KELLY"),
      TestData(id(4), "Gino BARTALI"),
      TestData(id(5), "Francesco MOSER"),
      TestData(id(11), "Tadej POGAČAR"),
      TestData(id(12), "Jasper PHILIPSEN"),
      TestData(id(13), "Mads PEDERSEN"),
      TestData(id(14), "Juan AYUSO PESQUERA"),
      TestData(id(15), "Matteo JORGENSON"),
      TestData(id(21), "uae_team_emirates"),
      TestData(id(22), "team_visma_lease_a_bike"),
      TestData(id(23), "lidl_trek"),
      TestData(id(31), "Über den Wolken"),
      TestData(id(32), "thé café")
    )
    def get(num: Int): TestData =
      sample
        .find(_.id == id(num))
        .getOrElse(sys.error(s"Literal test data not found: $id"))

    given Encoder[TestData] = MapBasedCodecs.deriveEncoder
    given Decoder[TestData] = MapBasedCodecs.deriveDecoder
