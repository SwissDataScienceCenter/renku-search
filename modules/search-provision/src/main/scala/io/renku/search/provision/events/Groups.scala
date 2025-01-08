package io.renku.search.provision.events

import io.renku.events.v2
import io.renku.events.v2.GroupMemberAdded
import io.renku.events.v2.GroupMemberUpdated
import io.renku.search.events.syntax.*
import io.renku.search.solr.documents.{Group as GroupDocument, PartialEntityDocument}
import io.renku.solr.client.DocVersion

trait Groups:

  def fromGroupAdded(ga: v2.GroupAdded, version: DocVersion): GroupDocument =
    GroupDocument(
      id = ga.id.toId,
      version = version,
      name = ga.name.toName,
      namespace = ga.namespace.toNamespace,
      description = ga.description.map(_.toDescription)
    )

  def fromGroupUpdated(
      ga: v2.GroupUpdated,
      version: DocVersion
  ): PartialEntityDocument.Group =
    PartialEntityDocument.Group(
      id = ga.id.toId,
      version = version,
      name = Some(ga.name.toName),
      namespace = Some(ga.namespace.toNamespace),
      description = ga.description.map(_.toDescription)
    )

  def fromGroupUpdated(gu: v2.GroupUpdated, orig: GroupDocument): GroupDocument =
    orig.copy(
      id = gu.id.toId,
      name = gu.name.toName,
      namespace = gu.namespace.toNamespace,
      description = gu.description.map(_.toDescription)
    )

  def fromGroupUpdated(
      gu: v2.GroupUpdated,
      orig: PartialEntityDocument.Group
  ): PartialEntityDocument.Group =
    orig.copy(
      id = gu.id.toId,
      name = Some(gu.name.toName),
      namespace = Some(gu.namespace.toNamespace),
      description = gu.description.map(_.toDescription)
    )

  def fromGroupMemberAdded(
      ga: GroupMemberAdded,
      version: DocVersion
  ): PartialEntityDocument.Group =
    PartialEntityDocument
      .Group(
        id = ga.groupId.toId,
        version = version
      )
      .modifyEntityMembers(_.addMember(ga.userId.toId, ga.role.toModel))

  def fromGroupMemberUpdated(
      ga: GroupMemberUpdated,
      version: DocVersion
  ): PartialEntityDocument.Group =
    PartialEntityDocument
      .Group(
        id = ga.groupId.toId,
        version = version
      )
      .modifyEntityMembers(_.addMember(ga.userId.toId, ga.role.toModel))
