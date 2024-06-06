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

package io.renku.search.solr.query

import cats.effect.IO
import cats.syntax.all.*

import io.renku.search.GeneratorSyntax.*
import io.renku.search.model.{Id, MemberRole, Visibility}
import io.renku.search.query.Query
import io.renku.search.solr.client.SolrDocumentGenerators
import io.renku.search.solr.documents.*
import org.scalacheck.Gen
import org.scalacheck.cats.implicits.*

final case class AuthTestData(
    user1: User,
    user2: User,
    user3: User,
    projects: Map[AuthTestData.UserProjectKey, Project]
):
  val users = List(user1, user2, user3)
  val all: List[EntityDocument] = users ++ projects.values.toList
  val user1PublicProject: Project = projects(user1.id -> Visibility.Public)
  val user2PublicProject: Project = projects(user2.id -> Visibility.Public)
  val user3PublicProject: Project = projects(user3.id -> Visibility.Public)
  val user1PrivateProject: Project = projects(user1.id -> Visibility.Private)
  val user2PrivateProject: Project = projects(user2.id -> Visibility.Private)
  val user3PrivateProject: Project = projects(user3.id -> Visibility.Private)

  def modifyProject(key: AuthTestData.UserProjectKey)(
      f: Project => Project
  ): AuthTestData =
    val p = f(projects(key))
    copy(projects = projects.updated(key, p))

  def queryAll =
    Query(Query.Segment.idIs(all.head.id.value, all.tail.map(_.id.value)*))

  def user1EntityIds =
    users.map(_.id) ++ List(
      user1PublicProject,
      user1PrivateProject,
      user2PublicProject,
      user2PrivateProject,
      user3PublicProject
    ).map(_.id)

  def user2EntityIds =
    users.map(_.id) ++ List(
      user1PublicProject,
      user2PublicProject,
      user2PrivateProject,
      user3PublicProject,
      user3PrivateProject
    ).map(_.id)

  def user3EntityIds =
    users.map(_.id) ++ List(
      user1PublicProject,
      user2PublicProject,
      user3PublicProject,
      user3PrivateProject
    ).map(_.id)

  def publicEntityIds =
    users.map(_.id) ++ List(
      user1PublicProject,
      user2PublicProject,
      user3PublicProject
    ).map(_.id)

  private def setupRelations =
    // user1 is member of user2 private project
    modifyProject(user2.id -> Visibility.Private)(
      _.modifyEntityMembers(
        _.addMember(
          user1.id,
          Gen.oneOf(MemberRole.values.toSet - MemberRole.Owner).generateOne
        )
      )
    )
      // user2 is owner of user3 private project
      .modifyProject(user3.id -> Visibility.Private)(
        _.modifyEntityMembers(_.addMember(user2.id, MemberRole.Owner))
      )

object AuthTestData:
  private type UserProjectKey = (Id, Visibility)
  private def projectGen(user: User, vis: Visibility) =
    SolrDocumentGenerators
      .projectDocumentGen(
        s"user${user.id}-${vis.name}-proj",
        "description",
        Gen.const(vis)
      )
      .map(p => (user.id, vis) -> p.copy(owners = Set(user.id)))

  val generator: Gen[AuthTestData] = for {
    u1 <- SolrDocumentGenerators.userDocumentGen
    u2 <- SolrDocumentGenerators.userDocumentGen
    u3 <- SolrDocumentGenerators.userDocumentGen
    projects <- Visibility.values.toList
      .flatMap(v => List(u1, u2, u3).map(_ -> v))
      .traverse { case (user, vis) =>
        projectGen(user, vis)
      }
  } yield AuthTestData(u1, u2, u3, projects.toMap).setupRelations

  def generate: IO[AuthTestData] = IO(generator.generateOne)
