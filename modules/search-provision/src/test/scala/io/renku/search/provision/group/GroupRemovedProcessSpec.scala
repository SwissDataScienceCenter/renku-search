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

package io.renku.search.provision.group

import cats.effect.IO
import cats.syntax.all.*

import io.renku.events.EventsGenerators
import io.renku.search.GeneratorSyntax.*
import io.renku.search.events.GroupRemoved
import io.renku.search.model.{EntityType, Id}
import io.renku.search.provision.ProvisioningSuite
import io.renku.search.solr.client.SearchSolrClient
import io.renku.search.solr.client.SolrDocumentGenerators
import io.renku.search.solr.client.SolrDocumentGenerators.*
import io.renku.search.solr.documents.DocumentKind
import io.renku.search.solr.documents.{
  Group as GroupDocument,
  Project as ProjectDocument,
  *
}
import io.renku.search.solr.query.SolrToken
import io.renku.solr.client.DocVersion
import io.renku.solr.client.QueryData
import io.renku.solr.client.QueryString
import org.scalacheck.Gen

class GroupRemovedProcessSpec extends ProvisioningSuite:

  test(
    "can fetch events, decode them, remove the Group from Solr, " +
      "and turn all the group's project to partial in Solr"
  ):
    val initialState = GroupRemovedProcessSpec.DbState.create.generateOne
    for
      services <- IO(testServices())
      handler = services.messageHandlers
      queueClient = services.queueClient
      solrClient = services.searchClient

      _ <- initialState.setup(solrClient)
      init <- initialState.loadByIds(solrClient)
      _ = assertEquals(init.setVersion(DocVersion.NotExists), initialState)

      _ <- queueClient.enqueue(
        queueConfig.groupRemoved,
        EventsGenerators
          .eventMessageGen(Gen.const(GroupRemoved(initialState.group.id)))
          .generateOne
      )

      _ <- handler.makeGroupRemoved.take(1).compile.toList

      projects <- initialState.loadPartialProjects(solrClient)
      _ = assertEquals(projects.size, initialState.projects.size)
      _ = assertEquals(projects.map(_.id), initialState.projects.map(_.id))
    yield ()

object GroupRemovedProcessSpec:

  final case class DbState(group: GroupDocument, projects: Set[ProjectDocument]):
    def setVersion(v: DocVersion): DbState =
      copy(group.setVersion(v), projects.map(_.setVersion(v)))

    def setup(solrClient: SearchSolrClient[IO]): IO[Unit] =
      solrClient.upsertSuccess(Seq(group)) >> solrClient.upsertSuccess(projects.toList)

    def loadByIds(solrClient: SearchSolrClient[IO]): IO[DbState] =
      for
        group <- solrClient.findById[EntityDocument](CompoundId.groupEntity(group.id))
        pq = List(
          SolrToken.kindIs(DocumentKind.FullEntity),
          SolrToken.entityTypeIs(EntityType.Project),
          projects.toList.map(p => SolrToken.idIs(p.id)).foldOr
        ).foldAnd
        projs <- solrClient.query[EntityDocument](
          QueryData(QueryString(q = pq.value, limit = projects.size, offset = 0))
        )
      yield DbState(
        group.get.asInstanceOf[GroupDocument],
        projs.responseBody.docs.map(_.asInstanceOf[ProjectDocument]).toSet
      )

    def loadPartialProjects(
        solrClient: SearchSolrClient[IO]
    ): IO[Set[PartialEntityDocument.Project]] =
      val pq = List(
        SolrToken.kindIs(DocumentKind.PartialEntity),
        SolrToken.entityTypeIs(EntityType.Project),
        projects.toList.map(p => SolrToken.idIs(p.id)).foldOr
      ).foldAnd
      solrClient
        .query[PartialEntityDocument](QueryData(QueryString(pq.value)))
        .map(_.responseBody.docs.map(_.asInstanceOf[PartialEntityDocument.Project]).toSet)

  object DbState:
    def create: Gen[DbState] =
      for
        group <- SolrDocumentGenerators.groupDocumentGen
        projects <- SolrDocumentGenerators.projectDocumentGen.asListOfN()
      yield DbState(group, projects.toSet.map(_.copy(namespace = group.namespace.some)))
