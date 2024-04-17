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

package io.renku.search.cli.perftests

import cats.MonadThrow
import cats.syntax.all.*
import fs2.Stream
import io.renku.events.v1.*
import io.renku.events.v1.ProjectMemberRole.OWNER
import io.renku.search.model.{Id, Name}
import io.renku.search.solr.documents.{Project, User}

private trait ProjectEventsGenerator[F[_]]:
  def newProjectEvents: Stream[F, NewProjectEvents]

private object ProjectEventsGenerator:
  def apply[F[_]: MonadThrow: ModelTypesGenerators](
      docsCreator: DocumentsCreator[F]
  ): ProjectEventsGenerator[F] =
    new ProjectEventsGeneratorImpl[F](docsCreator)

private class ProjectEventsGeneratorImpl[F[_]: MonadThrow: ModelTypesGenerators](
    docsCreator: DocumentsCreator[F]
) extends ProjectEventsGenerator[F]:

  private val gens = ModelTypesGenerators[F]

  override def newProjectEvents: Stream[F, NewProjectEvents] =
    docsCreator.findProject
      .evalMap(toNewProjectEvents)

  private def toNewProjectEvents(t: (Project, List[User])): F[NewProjectEvents] =
    (toProjectCreated(t), toUserAdded(t), toProjectAuthAdded(t))
      .mapN(NewProjectEvents.apply)

  private lazy val toProjectCreated: ((Project, List[User])) => F[ProjectCreated] = {
    case (project, users) =>
      users.headOption
        .fold(gens.generateId)(_.id.pure[F])
        .map(creator =>
          ProjectCreated(
            project.id.value,
            project.name.value,
            project.slug.value,
            project.repositories.map(_.value),
            Visibility.valueOf(project.visibility.name.toUpperCase),
            project.description.map(_.value),
            project.keywords.map(_.value),
            creator.value,
            project.creationDate.value
          )
        )
  }

  private lazy val toUserAdded: ((Project, List[User])) => F[List[UserAdded]] = {
    case (_, users) =>
      users
        .map(u =>
          UserAdded(
            u.id.value,
            u.firstName.map(_.value),
            u.lastName.map(_.value),
            email = None
          )
        )
        .pure[F]
  }

  private lazy val toProjectAuthAdded
      : ((Project, List[User])) => F[List[ProjectAuthorizationAdded]] = {
    case (project, owner :: users) =>
      val memberAuth = ProjectAuthorizationAdded(project.id.value, owner.id.value, OWNER)
      users
        .map { u =>
          gens.generateV1MemberRole
            .map(ProjectAuthorizationAdded(project.id.value, u.id.value, _))
        }
        .sequence
        .map(memberAuth :: _)
    case _ => List.empty.pure[F]
  }
