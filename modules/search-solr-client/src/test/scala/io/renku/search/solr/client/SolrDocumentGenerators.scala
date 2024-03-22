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
import io.renku.search.model.ModelGenerators.*
import io.renku.search.model.*
import io.renku.search.model.projects.Visibility
import io.renku.search.solr.documents.*
import org.scalacheck.Gen
import org.scalacheck.cats.implicits.*

object SolrDocumentGenerators extends SolrDocumentGenerators

trait SolrDocumentGenerators:

  private def projectIdGen: Gen[Id] =
    Gen.uuid.map(uuid => Id(uuid.toString))

  val partialProjectGen: Gen[PartialEntityDocument.Project] =
    val ids = Gen.choose(1, 5).flatMap(n => Gen.listOfN(n, idGen)).map(_.toSet)
    (projectIdGen, ids, ids).mapN(PartialEntityDocument.Project.apply)

  val projectDocumentGen: Gen[Project] =
    val differentiator = nameGen.generateOne
    projectDocumentGen(
      s"proj-$differentiator",
      s"proj desc $differentiator"
    )

  def projectDocumentGen(
      name: String,
      desc: String,
      visibilityGen: Gen[Visibility] = projectVisibilityGen
  ): Gen[Project] =
    (projectIdGen, idGen, visibilityGen, projectCreationDateGen)
      .mapN((projectId, creatorId, visibility, creationDate) =>
        Project(
          projectId,
          Name(name),
          projects.Slug(name),
          Seq(projects.Repository(s"http://github.com/$name")),
          visibility,
          Option(projects.Description(desc)),
          creatorId,
          creationDate
        )
      )

  def userDocumentGen: Gen[User] =
    (idGen, Gen.option(userFirstNameGen), Gen.option(userLastNameGen))
      .flatMapN { case (id, f, l) =>
        User.of(id, f, l)
      }
