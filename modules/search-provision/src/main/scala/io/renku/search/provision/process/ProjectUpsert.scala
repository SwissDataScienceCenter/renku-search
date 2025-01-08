package io.renku.search.provision.process

import cats.effect.*
import cats.syntax.all.*

import io.renku.search.events.*
import io.renku.search.provision.handler.*
import io.renku.search.provision.handler.PipelineSteps
import io.renku.search.solr.documents.{
  EntityMembers,
  PartialEntityDocument,
  Project as ProjectDocument
}
import io.renku.solr.client.UpsertResponse
import io.renku.solr.client.UpsertResponse.syntax.*

/** Process insert and update to a project.
  *
  * This is a specific upsert for messages updating/creating a project. It works similar
  * to the [[GenericUpsert]], but it must also update the group members of any project
  * document that got newly inserted.
  */
final private[provision] class ProjectUpsert[F[_]: Async](ps: PipelineSteps[F]):

  def process[A](
      msg: EventMessage[A],
      retries: Int
  )(using IdExtractor[A], DocumentMerger[A]): F[UpsertResponse] =
    process1(msg).retryOnConflict(retries)

  def process1[A](
      msg: EventMessage[A]
  )(using IdExtractor[A], DocumentMerger[A]): F[UpsertResponse] =
    for
      loadInitial <- ps.fetchFromSolr.loadEntityOrPartial(msg)
      merger = DocumentMerger[A]
      entity = loadInitial.merge(merger.create, merger.merge)
      withGroups <- ps.fetchFromSolr.loadProjectGroups(entity)
      updated = updateGroupMembers(withGroups).asMessage
      resp <- ps.pushToSolr.pushAll(updated)
    yield resp

  /** Goes through all projects and updates its group members based on the group that is
    * searched in the same payload
    */
  private def updateGroupMembers(m: EntityOrPartialMessage[EntityOrPartial]) =
    val updatedProjects: Seq[EntityOrPartial] =
      m.message.payload.flatMap {
        case p: ProjectDocument =>
          p.namespace.flatMap(m.findGroupByNs) match
            case Some(group) =>
              p.setGroupMembers(group.toEntityMembers).some
            case None =>
              p.setGroupMembers(EntityMembers.empty).some
        case p: PartialEntityDocument.Project =>
          p.namespace.flatMap(m.findGroupByNs) match
            case Some(group) =>
              p.setGroupMembers(group.toEntityMembers).some
            case None =>
              p.setGroupMembers(EntityMembers.empty).some
        case _ => None
      }
    m.setDocuments(updatedProjects.toList)
