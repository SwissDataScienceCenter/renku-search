/*
 * Copyright 2024 Swiss Data Science Center (SDSC)
 * A partnership between École Polytechnique Fédérale de Lausanne (EPFL) and
 * Eidgenössische Technische Hochschule Zürich (ETHZ).
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.renku.search.provision.handler

import cats.syntax.all.*

import io.github.arainko.ducktape.*
import io.renku.events.v1.*
import io.renku.search.model.{Id, Name}
import io.renku.search.model.projects.{Description, Slug, Visibility}
import io.renku.search.provision.handler.TypeTransformers.given
import io.renku.search.solr.documents.EntityDocument
import io.renku.search.solr.documents.PartialEntityDocument
import io.renku.search.solr.documents.Project as ProjectDocument
import io.renku.search.solr.documents.User as UserDocument
import io.renku.solr.client.DocVersion
import io.renku.search.model.projects.Repository

trait DocumentMerger[A]:
  def create(value: A): Option[EntityOrPartial]
  def merge(value: A, existing: EntityOrPartial): Option[EntityOrPartial]

object DocumentMerger:
  def apply[A](using dm: DocumentMerger[A]): DocumentMerger[A] = dm

  def instance[A](
      onCreate: A => Option[EntityOrPartial]
  )(onMerge: (A, EntityOrPartial) => Option[EntityOrPartial]): DocumentMerger[A] =
    new DocumentMerger[A] {
      def create(value: A) = onCreate(value)
      def merge(value: A, existing: EntityOrPartial) = onMerge(value, existing)
    }

  given DocumentMerger[ProjectAuthorizationAdded] =
    instance[ProjectAuthorizationAdded](
      _.to[PartialEntityDocument].setVersion(DocVersion.NotExists).some
    )((paa, existing) =>
      existing match
        case p: PartialEntityDocument.Project =>
          p.applyTo(paa.to[PartialEntityDocument]).some
        case p: EntityDocument =>
          paa.to[PartialEntityDocument].applyTo(p).some
    )

  given DocumentMerger[ProjectAuthorizationUpdated] =
    instance[ProjectAuthorizationUpdated](
      _.to[PartialEntityDocument].setVersion(DocVersion.NotExists).some
    )((pau, existing) =>
      existing match
        case p: PartialEntityDocument.Project =>
          p.remove(pau.userId.to[Id]).applyTo(pau.to[PartialEntityDocument]).some
        case p: ProjectDocument =>
          pau.to[PartialEntityDocument].applyTo(p.removeMember(pau.userId.to[Id])).some
        case _ => None
    )

  given DocumentMerger[ProjectAuthorizationRemoved] =
    instance[ProjectAuthorizationRemoved](_ => None)((par, existing) =>
      existing match
        case p: PartialEntityDocument.Project =>
          p.remove(par.userId.to[Id]).some
        case p: ProjectDocument =>
          p.removeMember(par.userId.to[Id]).some

        case _ => None
    )

  given DocumentMerger[ProjectCreated] =
    def convert(pc: ProjectCreated): ProjectDocument =
      pc.into[ProjectDocument]
        .transform(
          Field.const(_.`_version_`, DocVersion.NotExists),
          Field.default(_.owners),
          Field.default(_.members),
          Field.default(_.score)
        )

    instance[ProjectCreated](convert(_).some)((pc, existing) =>
      existing match
        case p: PartialEntityDocument.Project =>
          p.applyTo(convert(pc)).some
        case p: ProjectDocument =>
          // already exists, but we overwrite
          Some(
            convert(pc)
              .setVersion(p._version_)
              .copy(owners = p.owners, members = p.members)
          )
        case _: UserDocument =>
          None
    )

  given DocumentMerger[ProjectUpdated] =
    instance[ProjectUpdated](_ => None)((pu, existing) =>
      existing match
        case p: PartialEntityDocument.Project =>
          p.copy(
            name = Name(pu.name).some,
            slug = Slug(pu.slug).some,
            repositories = pu.repositories.map(Repository.apply),
            visibility = pu.visibility.to[Visibility].some,
            description = pu.description.map(_.to[Description])
          ).some

        case orig: ProjectDocument =>
          pu
            .into[ProjectDocument]
            .transform(
              Field.const(_.`_version_`, orig._version_),
              Field.const(_.createdBy, orig.createdBy),
              Field.const(_.creationDate, orig.creationDate),
              Field.const(_.owners, orig.owners),
              Field.const(_.members, orig.members),
              Field.default(_.score)
            )
            .some
        case _ => None
    )

  given DocumentMerger[UserAdded] =
    def convert(ua: UserAdded): UserDocument =
      ua.into[UserDocument]
        .transform(
          Field.const(_.`_version_`, DocVersion.NotExists),
          Field.default(_.score),
          Field.computed(_.name, u => UserDocument.nameFrom(u.firstName, u.lastName))
        )
    instance[UserAdded](convert(_).some)((ua, existing) =>
      existing match
        case u: EntityDocument        => Some(convert(ua).setVersion(u._version_))
        case _: PartialEntityDocument => None
    )

  given DocumentMerger[UserUpdated] =
    instance[UserUpdated](_ => None)((uu, existing) =>
      existing match
        case orig: UserDocument =>
          uu
            .into[UserDocument]
            .transform(
              Field.const(_._version_, orig._version_),
              Field.default(_.score),
              Field.computed(_.name, u => UserDocument.nameFrom(u.firstName, u.lastName))
            )
            .some
        case _ => None
    )
