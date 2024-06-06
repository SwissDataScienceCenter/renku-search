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

package io.renku.search.solr.client

import cats.syntax.all.*

import io.renku.search.GeneratorSyntax.*
import io.renku.search.model.*
import io.renku.search.model.ModelGenerators.*
import io.renku.search.model.Visibility
import io.renku.search.solr.documents.*
import io.renku.solr.client.DocVersion
import io.renku.solr.client.ResponseBody
import org.scalacheck.Gen
import org.scalacheck.Gen.const
import org.scalacheck.cats.implicits.*

object SolrDocumentGenerators extends SolrDocumentGenerators

trait SolrDocumentGenerators:

  private def idGen: Gen[Id] =
    Gen.uuid.map(uuid => Id(uuid.toString))

  val partialProjectGen: Gen[PartialEntityDocument.Project] =
    (idGen, const(DocVersion.Off), entityMembersGen)
      .mapN((id, v, mem) =>
        PartialEntityDocument.Project(id = id, version = v).setMembers(mem)
      )

  val projectDocumentGen: Gen[Project] =
    val differentiator = nameGen.generateOne
    projectDocumentGen(
      s"proj-$differentiator",
      s"proj desc $differentiator",
      userDocumentGen.asOption
    )

  val projectDocumentGenForInsert: Gen[Project] =
    val differentiator = nameGen.generateOne
    projectDocumentGen(
      s"proj-$differentiator",
      s"proj desc $differentiator",
      Gen.const(None)
    )

  def projectDocumentGen(
      name: String,
      desc: String,
      creatorGen: Gen[Option[User]],
      visibilityGen: Gen[Visibility] = visibilityGen
  ): Gen[Project] =
    (idGen, idGen, visibilityGen, creationDateGen, creatorGen)
      .mapN((projectId, creatorId, visibility, creationDate, creator) =>
        Project(
          projectId,
          DocVersion.NotExists,
          Name(name),
          Slug(name),
          Seq(Repository(s"http://github.com/$name")),
          visibility,
          Option(Description(desc)),
          creatorId,
          creator.map(_.copy(id = creatorId)).map(ResponseBody.single),
          creationDate
        )
      )

  def userDocumentGen: Gen[User] =
    (
      idGen,
      Gen.option(userFirstNameGen),
      Gen.option(userLastNameGen),
      Gen.option(ModelGenerators.namespaceGen)
    )
      .flatMapN { case (id, f, l, ns) =>
        User.of(id, ns, f, l)
      }

  lazy val entityMembersGen: Gen[EntityMembers] =
    val ids = Gen.choose(1, 5).flatMap(n => Gen.listOfN(n, idGen)).map(_.toSet)
    (ids, ids, ids, ids)
      .mapN((own, edi, view, mem) => EntityMembers(own, edi, view, mem))

  lazy val groupDocumentGen: Gen[Group] =
    (idGen, idGen, groupNameGen, namespaceGen, Gen.option(groupDescGen))
      .mapN((groupId, creatorId, name, namespace, desc) =>
        Group(groupId, DocVersion.NotExists, name, namespace, desc)
      )

  val partialGroupGen: Gen[PartialEntityDocument.Group] =
    (idGen, idGen, groupNameGen, namespaceGen, Gen.option(groupDescGen))
      .mapN((groupId, creatorId, name, namespace, desc) =>
        PartialEntityDocument.Group(
          groupId,
          DocVersion.NotExists,
          Some(name),
          Some(namespace),
          desc
        )
      )
