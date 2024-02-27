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
import io.renku.scalacheck
import io.renku.search.model.*
import io.renku.search.solr.documents.*
import org.scalacheck.Gen

import java.time.Instant
import java.time.temporal.ChronoUnit

object SearchSolrClientGenerators extends scalacheck.all:

  private def projectIdGen: Gen[projects.Id] =
    Gen.uuid.map(uuid => projects.Id(uuid.toString))

  def projectDocumentGen(name: String, desc: String): Gen[Project] =
    (projectIdGen, userIdGen).mapN((projectId, creatorId) =>
      Project(
        projectId,
        projects.Name(name),
        projects.Slug(name),
        Seq(projects.Repository(s"http://github.com/$name")),
        Gen.oneOf(projects.Visibility.values.toList).generateOne,
        Option(projects.Description(desc)),
        creatorId,
        instantGen().generateAs(projects.CreationDate.apply),
        Seq(creatorId)
      )
    )

  def userDocumentGen: Gen[User] =
    userIdGen.map(id => User(id))

  private def userIdGen: Gen[users.Id] = Gen.uuid.map(uuid => users.Id(uuid.toString))

  private def instantGen(
      min: Instant = Instant.EPOCH,
      max: Instant = Instant.now()
  ): Gen[Instant] =
    Gen
      .chooseNum(min.toEpochMilli, max.toEpochMilli)
      .map(Instant.ofEpochMilli(_).truncatedTo(ChronoUnit.MILLIS))
