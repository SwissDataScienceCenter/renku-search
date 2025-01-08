package io.renku.search.provision.events

import io.renku.events.v1.{ProjectAuthorizationAdded, ProjectAuthorizationUpdated}
import io.renku.events.v2.ProjectMemberAdded
import io.renku.events.v2.ProjectMemberUpdated
import io.renku.events.{v1, v2}
import io.renku.search.events.syntax.*
import io.renku.search.solr.documents.PartialEntityDocument
import io.renku.solr.client.DocVersion

trait ProjectAuthorization:

  def fromProjectAuthorizationAdded(
      paa: ProjectAuthorizationAdded,
      version: DocVersion
  ): PartialEntityDocument.Project =
    PartialEntityDocument
      .Project(id = paa.projectId.toId, version = version)
      .modifyEntityMembers(_.addMember(paa.userId.toId, paa.role.toModel))

  def fromProjectMemberAdded(
      paa: ProjectMemberAdded,
      version: DocVersion
  ): PartialEntityDocument.Project =
    PartialEntityDocument
      .Project(id = paa.projectId.toId, version = version)
      .modifyEntityMembers(_.addMember(paa.userId.toId, paa.role.toModel))

  def fromProjectAuthorizationUpdated(
      paa: ProjectAuthorizationUpdated,
      version: DocVersion
  ): PartialEntityDocument.Project =
    PartialEntityDocument
      .Project(id = paa.projectId.toId, version = version)
      .modifyEntityMembers(_.addMember(paa.userId.toId, paa.role.toModel))

  def fromProjectMemberUpdated(
      paa: ProjectMemberUpdated,
      version: DocVersion
  ): PartialEntityDocument.Project =
    PartialEntityDocument
      .Project(id = paa.projectId.toId, version = version)
      .modifyEntityMembers(_.addMember(paa.userId.toId, paa.role.toModel))
