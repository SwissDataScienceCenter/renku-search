package io.renku.search.cli.perftests

import cats.effect.Sync
import cats.syntax.all.*

import io.renku.redis.client.QueueName
import io.renku.search.events.*

final private case class NewProjectEvents(
    projectCreated: ProjectCreated,
    users: List[UserAdded],
    authAdded: List[ProjectMemberAdded]
):
  private val messageSource = MessageSource("perf-tests")

  def toQueueDelivery[F[_]: Sync: ModelTypesGenerators]: F[List[QueueDelivery]] =
    (projectToDelivery ::
      users.map(userToDelivery) :::
      authAdded.map(authToDelivery)).sequence

  private def projectToDelivery[F[_]: Sync: ModelTypesGenerators]: F[QueueDelivery] =
    createMessage[F, ProjectCreated](projectCreated).map(m =>
      QueueDelivery(QueueName("project.created"), m)
    )

  private def userToDelivery[F[_]: Sync: ModelTypesGenerators](
      p: UserAdded
  ): F[QueueDelivery] =
    createMessage[F, UserAdded](p).map(m => QueueDelivery(QueueName("user.added"), m))

  private def authToDelivery[F[_]: Sync: ModelTypesGenerators](
      p: ProjectMemberAdded
  ): F[QueueDelivery] =
    createMessage[F, ProjectMemberAdded](p).map(m =>
      QueueDelivery(QueueName("projectAuth.added"), m)
    )

  private def createMessage[F[_], A <: RenkuEventPayload](
      pl: A
  )(using Sync[F], ModelTypesGenerators[F]): F[EventMessage[A]] =
    ModelTypesGenerators[F].generateRequestId.flatMap { reqId =>
      EventMessage.create[F, A](
        MessageId("*"),
        messageSource,
        DataContentType.Binary,
        reqId,
        pl
      )
    }
