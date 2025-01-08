package io.renku.search.provision.events

import cats.syntax.all.*

import io.renku.events.{v1, v2}
import io.renku.search.events.syntax.*
import io.renku.search.solr.documents.PartialEntityDocument
import io.renku.search.solr.documents.Project as ProjectDocument
import io.renku.solr.client.DocVersion

trait Projects:

  def partialProjectFromDocument(pd: ProjectDocument): PartialEntityDocument.Project =
    PartialEntityDocument
      .Project(
        id = pd.id,
        version = pd.version,
        name = pd.name.some,
        slug = pd.slug.some,
        repositories = pd.repositories,
        visibility = pd.visibility.some,
        description = pd.description,
        keywords = pd.keywords
      )
      .setMembers(pd.toEntityMembers)

  def fromProjectCreated(pc: v1.ProjectCreated, version: DocVersion): ProjectDocument =
    ProjectDocument(
      id = pc.id.toId,
      version = version,
      name = pc.name.toName,
      slug = pc.slug.toSlug,
      repositories = pc.repositories.map(_.toRepository),
      visibility = pc.visibility.toModel,
      description = pc.description.map(_.toDescription),
      keywords = pc.keywords.map(_.toKeyword).toList,
      createdBy = pc.createdBy.toId,
      creatorDetails = None,
      creationDate = pc.creationDate.toCreationDate
    )

  def fromProjectCreated(pc: v2.ProjectCreated, version: DocVersion): ProjectDocument =
    ProjectDocument(
      id = pc.id.toId,
      version = version,
      name = pc.name.toName,
      namespace = pc.namespace.toNamespace.some,
      slug = pc.slug.toSlug,
      repositories = pc.repositories.map(_.toRepository),
      visibility = pc.visibility.toModel,
      description = pc.description.map(_.toDescription),
      keywords = pc.keywords.map(_.toKeyword).toList,
      createdBy = pc.createdBy.toId,
      creationDate = pc.creationDate.toCreationDate,
      creatorDetails = None
    )

  def fromProjectUpdated(pu: v1.ProjectUpdated, orig: ProjectDocument): ProjectDocument =
    orig.copy(
      id = pu.id.toId,
      version = orig.version,
      name = pu.name.toName,
      slug = pu.slug.toSlug,
      repositories = pu.repositories.map(_.toRepository),
      visibility = pu.visibility.toModel,
      description = pu.description.map(_.toDescription),
      keywords = pu.keywords.map(_.toKeyword).toList,
      score = None
    )

  def fromProjectUpdated(pu: v2.ProjectUpdated, orig: ProjectDocument): ProjectDocument =
    orig.copy(
      id = pu.id.toId,
      version = orig.version,
      name = pu.name.toName,
      namespace = pu.namespace.toNamespace.some,
      slug = pu.slug.toSlug,
      repositories = pu.repositories.map(_.toRepository),
      visibility = pu.visibility.toModel,
      description = pu.description.map(_.toDescription),
      keywords = pu.keywords.map(_.toKeyword).toList,
      score = None
    )

  def fromProjectUpdated(
      pu: v1.ProjectUpdated,
      orig: PartialEntityDocument.Project
  ): PartialEntityDocument.Project =
    orig.copy(
      name = pu.name.toName.some,
      slug = pu.slug.toSlug.some,
      repositories = pu.repositories.map(_.toRepository),
      visibility = pu.visibility.toModel.some,
      description = pu.description.map(_.toDescription)
    )

  def fromProjectUpdated(
      pu: v2.ProjectUpdated,
      orig: PartialEntityDocument.Project
  ): PartialEntityDocument.Project =
    orig.copy(
      name = pu.name.toName.some,
      namespace = pu.namespace.toNamespace.some,
      slug = pu.slug.toSlug.some,
      repositories = pu.repositories.map(_.toRepository),
      visibility = pu.visibility.toModel.some,
      description = pu.description.map(_.toDescription)
    )

  def fromProjectUpdated(
      pu: v1.ProjectUpdated,
      version: DocVersion
  ): PartialEntityDocument.Project =
    PartialEntityDocument.Project(
      id = pu.id.toId,
      version = version,
      name = pu.name.toName.some,
      slug = pu.slug.toSlug.some,
      repositories = pu.repositories.map(_.toRepository),
      visibility = pu.visibility.toModel.some,
      description = pu.description.map(_.toDescription),
      keywords = pu.keywords.map(_.toKeyword).toList
    )

  def fromProjectUpdated(
      pu: v2.ProjectUpdated,
      version: DocVersion
  ): PartialEntityDocument.Project =
    PartialEntityDocument.Project(
      id = pu.id.toId,
      version = version,
      name = pu.name.toName.some,
      namespace = pu.namespace.toNamespace.some,
      slug = pu.slug.toSlug.some,
      repositories = pu.repositories.map(_.toRepository),
      visibility = pu.visibility.toModel.some,
      description = pu.description.map(_.toDescription),
      keywords = pu.keywords.map(_.toKeyword).toList
    )
