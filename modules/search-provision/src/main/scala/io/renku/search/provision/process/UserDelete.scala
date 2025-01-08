package io.renku.search.provision.process

import cats.effect.*
import cats.syntax.all.*

import io.renku.search.events.EventMessage
import io.renku.search.events.UserRemoved
import io.renku.search.provision.handler.*

/** Delete a user.
  *
  * Deleting a user requires to update all affected entities to remove this user from
  * their members.
  */
final private[provision] class UserDelete[F[_]: Async](ps: PipelineSteps[F]):
  private val logger = scribe.cats.effect[F]

  /** Delete all users the payload. */
  def process(
      msg: EventMessage[UserRemoved]
  ): F[DeleteFromSolr.DeleteResult[UserRemoved]] =
    for
      _ <- logger.info(s"Deleting users for message: $msg")
      delRes <- ps.deleteFromSolr.deleteDocuments(msg)
      _ <- delRes match
        case DeleteFromSolr.DeleteResult.Success(_) =>
          ps.userUtils.removeMember(msg).compile.drain
        case _ =>
          ().pure[F]
    yield delRes
