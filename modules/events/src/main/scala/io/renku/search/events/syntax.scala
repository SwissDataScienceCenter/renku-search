package io.renku.search.events

import java.time.Instant

import io.renku.events.{v1, v2}
import io.renku.search.model.*

trait syntax:
  extension (self: v1.Visibility)
    def toModel: Visibility =
      Visibility.unsafeFromString(self.name())

  extension (self: v2.Visibility)
    def toModel: Visibility =
      Visibility.unsafeFromString(self.name())

  extension (self: v1.ProjectMemberRole)
    def toModel: MemberRole =
      MemberRole.unsafeFromString(self.name())

  extension (self: v2.MemberRole)
    def toModel: MemberRole =
      MemberRole.unsafeFromString(self.name())

  extension (self: String)
    def toId: Id = Id(self)
    def toName: Name = Name(self)
    def toNamespace: Namespace = Namespace(self)
    def toSlug: Slug = Slug(self)
    def toRepository: Repository = Repository(self)
    def toDescription: Description = Description(self)
    def toFirstName: FirstName = FirstName(self)
    def toLastName: LastName = LastName(self)
    def toKeyword: Keyword = Keyword(self)

  extension (self: Instant) def toCreationDate: CreationDate = CreationDate(self)

object syntax extends syntax
