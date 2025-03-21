package io.renku.search.provision.events

import io.renku.events.{v1, v2}
import io.renku.search.events.*
import io.renku.search.solr.documents.PartialEntityDocument
import io.renku.search.solr.documents.{
  Group as GroupDocument,
  Project as ProjectDocument,
  User as UserDocument
}
import io.renku.solr.client.DocVersion

trait syntax extends io.renku.search.events.syntax:
  extension (self: ProjectDocument)
    def toPartialDocument: PartialEntityDocument.Project =
      Conversion.partialProjectFromDocument(self)

  extension (self: v1.ProjectAuthorizationAdded)
    def toModel(version: DocVersion): PartialEntityDocument =
      Conversion.fromProjectAuthorizationAdded(self, version)

  extension (self: v2.ProjectMemberAdded)
    def toModel(version: DocVersion): PartialEntityDocument =
      Conversion.fromProjectMemberAdded(self, version)

  extension (self: ProjectMemberAdded)
    def toModel(version: DocVersion): PartialEntityDocument =
      self.fold(_.toModel(version), _.toModel(version))

  extension (self: v1.ProjectAuthorizationUpdated)
    def toModel(version: DocVersion): PartialEntityDocument =
      Conversion.fromProjectAuthorizationUpdated(self, version)

  extension (self: v2.ProjectMemberUpdated)
    def toModel(version: DocVersion): PartialEntityDocument =
      Conversion.fromProjectMemberUpdated(self, version)

  extension (self: ProjectMemberUpdated)
    def toModel(version: DocVersion): PartialEntityDocument =
      self.fold(_.toModel(version), _.toModel(version))

  extension (self: v1.ProjectCreated)
    def toModel(version: DocVersion): ProjectDocument =
      Conversion.fromProjectCreated(self, version)

  extension (self: v2.ProjectCreated)
    def toModel(version: DocVersion): ProjectDocument =
      Conversion.fromProjectCreated(self, version)

  extension (self: ProjectCreated)
    def toModel(version: DocVersion): ProjectDocument =
      self.fold(_.toModel(version), _.toModel(version))

  extension (self: v1.ProjectUpdated)
    def toModel(orig: ProjectDocument): ProjectDocument =
      Conversion.fromProjectUpdated(self, orig)
    def toModel(orig: PartialEntityDocument.Project): PartialEntityDocument.Project =
      Conversion.fromProjectUpdated(self, orig)
    def toModel(version: DocVersion): PartialEntityDocument.Project =
      Conversion.fromProjectUpdated(self, version)

  extension (self: v2.ProjectUpdated)
    def toModel(orig: ProjectDocument): ProjectDocument =
      Conversion.fromProjectUpdated(self, orig)
    def toModel(orig: PartialEntityDocument.Project): PartialEntityDocument.Project =
      Conversion.fromProjectUpdated(self, orig)
    def toModel(version: DocVersion): PartialEntityDocument.Project =
      Conversion.fromProjectUpdated(self, version)

  extension (self: ProjectUpdated)
    def toModel(version: DocVersion): PartialEntityDocument.Project =
      self.fold(_.toModel(version), _.toModel(version))
    def toModel(orig: ProjectDocument): ProjectDocument =
      self.fold(_.toModel(orig), _.toModel(orig))
    def toModel(orig: PartialEntityDocument.Project): PartialEntityDocument.Project =
      self.fold(_.toModel(orig), _.toModel(orig))

  extension (self: v1.UserAdded)
    def toModel(version: DocVersion): UserDocument =
      Conversion.fromUserAdded(self, version)

  extension (self: v2.UserAdded)
    def toModel(version: DocVersion): UserDocument =
      Conversion.fromUserAdded(self, version)

  extension (self: UserAdded)
    def toModel(version: DocVersion): UserDocument =
      self.fold(_.toModel(version), _.toModel(version))

  extension (self: v1.UserUpdated)
    def toModel(orig: UserDocument): UserDocument =
      Conversion.fromUserUpdated(self, orig)

  extension (self: v2.UserUpdated)
    def toModel(orig: UserDocument): UserDocument =
      Conversion.fromUserUpdated(self, orig)

  extension (self: UserUpdated)
    def toModel(orig: UserDocument): UserDocument =
      self.fold(_.toModel(orig), _.toModel(orig))

  extension (self: v2.GroupAdded)
    def toModel(version: DocVersion): GroupDocument =
      Conversion.fromGroupAdded(self, version)

  extension (self: GroupAdded)
    def toModel(version: DocVersion): GroupDocument =
      self.fold(_.toModel(version))

  extension (self: v2.GroupUpdated)
    def toModel(version: DocVersion): PartialEntityDocument.Group =
      Conversion.fromGroupUpdated(self, version)
    def toModel(orig: GroupDocument): GroupDocument =
      Conversion.fromGroupUpdated(self, orig)
    def toModel(orig: PartialEntityDocument.Group): PartialEntityDocument.Group =
      Conversion.fromGroupUpdated(self, orig)

  extension (self: GroupUpdated)
    def toModel(version: DocVersion): PartialEntityDocument.Group =
      self.fold(_.toModel(version))
    def toModel(orig: GroupDocument): GroupDocument =
      self.fold(_.toModel(orig))
    def toModel(orig: PartialEntityDocument.Group): PartialEntityDocument.Group =
      self.fold(_.toModel(orig))

  extension (self: GroupMemberAdded)
    def toModel(version: DocVersion): PartialEntityDocument.Group =
      self.fold(Conversion.fromGroupMemberAdded(_, version))

  extension (self: GroupMemberUpdated)
    def toModel(version: DocVersion): PartialEntityDocument.Group =
      self.fold(Conversion.fromGroupMemberUpdated(_, version))

object syntax extends syntax
