package io.renku.search.provision.handler

import cats.effect.Async
import cats.syntax.all.*
import fs2.Stream

import io.renku.search.events.*
import io.renku.search.model.Id
import io.renku.search.provision.handler.FetchFromSolr.EntityId
import io.renku.search.solr.documents.{
  Group as GroupDocument,
  PartialEntityDocument,
  Project as ProjectDocument
}
import io.renku.solr.client.ResponseHeader
import io.renku.solr.client.UpsertResponse
import io.renku.solr.client.UpsertResponse.syntax.*

trait UserUtils[F[_]]:
  /** For a message containing user ids, all entities where the user is a member of are
    * obtained and the user is removed from any member properties. The ids of all affected
    * entities is are returned
    */
  def removeMember[A](msg: EventMessage[A])(using IdExtractor[A]): Stream[F, EntityId]

object UserUtils:
  def apply[F[_]: Async](
      fetchFromSolr: FetchFromSolr[F],
      pushToSolr: PushToSolr[F],
      reader: MessageReader[F],
      maxConflictRetries: Int
  ): UserUtils[F] =
    new UserUtils[F] {
      val logger = scribe.cats.effect[F]

      private def updateEntity(
          id: Id,
          modify: EntityOrPartial => Option[EntityOrPartial]
      ): F[UpsertResponse] =
        for
          entity <- fetchFromSolr.fetchEntityOrPartialById(id)
          updated = entity.flatMap(modify)
          r <- updated match
            case Some(e) => pushToSolr.push1(e)
            case None    => UpsertResponse.Success(ResponseHeader.empty).pure[F]
        yield r

      def removeMember[A](
          msg: EventMessage[A]
      )(using IdExtractor[A]): Stream[F, EntityId] =
        val ids = msg.payload.map(IdExtractor[A].getId)
        Stream
          .emits(ids)
          .flatMap(fetchFromSolr.fetchEntityForUser)
          .evalTap { entityId =>
            updateEntity(
              entityId.id,
              {
                case p: ProjectDocument =>
                  p.modifyEntityMembers(_.removeMembers(ids))
                    .modifyGroupMembers(_.removeMembers(ids))
                    .some
                case p: PartialEntityDocument.Project =>
                  p.modifyEntityMembers(_.removeMembers(ids))
                    .modifyGroupMembers(_.removeMembers(ids))
                    .some
                case g: GroupDocument =>
                  g.modifyEntityMembers(_.removeMembers(ids)).some

                case _ => None
              }
            ).retryOnConflict(maxConflictRetries)
          }
    }
