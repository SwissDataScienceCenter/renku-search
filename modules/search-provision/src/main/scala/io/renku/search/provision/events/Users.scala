package io.renku.search.provision.events

import cats.syntax.all.*

import io.renku.events.{v1, v2}
import io.renku.search.events.syntax.*
import io.renku.search.solr.documents.User as UserDocument
import io.renku.solr.client.DocVersion

trait Users:

  def fromUserAdded(ua: v1.UserAdded, version: DocVersion): UserDocument =
    UserDocument(
      id = ua.id.toId,
      version = version,
      firstName = ua.firstName.map(_.toFirstName),
      lastName = ua.lastName.map(_.toLastName),
      name = UserDocument.nameFrom(ua.firstName, ua.lastName),
      namespace = None
    )

  def fromUserAdded(ua: v2.UserAdded, version: DocVersion): UserDocument =
    UserDocument(
      id = ua.id.toId,
      version = version,
      firstName = ua.firstName.map(_.toFirstName),
      lastName = ua.lastName.map(_.toLastName),
      name = UserDocument.nameFrom(ua.firstName, ua.lastName),
      namespace = ua.namespace.toNamespace.some
    )

  def fromUserUpdated(ua: v1.UserUpdated, orig: UserDocument): UserDocument =
    orig.copy(
      id = ua.id.toId,
      firstName = ua.firstName.map(_.toFirstName),
      lastName = ua.lastName.map(_.toLastName),
      name = UserDocument.nameFrom(ua.firstName, ua.lastName),
      namespace = None,
      score = None
    )

  def fromUserUpdated(ua: v2.UserUpdated, orig: UserDocument): UserDocument =
    orig.copy(
      id = ua.id.toId,
      firstName = ua.firstName.map(_.toFirstName),
      lastName = ua.lastName.map(_.toLastName),
      name = UserDocument.nameFrom(ua.firstName, ua.lastName),
      namespace = ua.namespace.toNamespace.some,
      score = None
    )
