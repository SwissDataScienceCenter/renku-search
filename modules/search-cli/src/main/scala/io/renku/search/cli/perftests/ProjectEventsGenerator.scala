package io.renku.search.cli.perftests

import cats.MonadThrow
import cats.syntax.all.*
import fs2.Stream

import io.renku.search.events.*
import io.renku.search.model.{MemberRole, Timestamp}
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
            project.id,
            project.name,
            project.namespace.get,
            project.slug,
            project.visibility,
            creator,
            Timestamp(project.creationDate.value),
            project.repositories,
            project.description,
            project.keywords
          )
        )
  }

  private lazy val toUserAdded: ((Project, List[User])) => F[List[UserAdded]] = {
    case (_, users) =>
      users
        .map(u =>
          UserAdded(
            u.id,
            u.namespace.get,
            u.firstName,
            u.lastName,
            email = None
          )
        )
        .pure[F]
  }

  private lazy val toProjectAuthAdded
      : ((Project, List[User])) => F[List[ProjectMemberAdded]] = {
    case (project, owner :: users) =>
      val memberAuth = ProjectMemberAdded(project.id, owner.id, MemberRole.Owner)
      users
        .map { u =>
          gens.generateRole
            .map(ProjectMemberAdded(project.id, u.id, _))
        }
        .sequence
        .map(memberAuth :: _)
    case _ => List.empty.pure[F]
  }
