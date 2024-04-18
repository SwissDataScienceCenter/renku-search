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
import io.renku.events.{v1, v2}
import io.renku.search.model.Id
import io.renku.search.solr.documents.EntityDocument
import io.renku.search.solr.documents.PartialEntityDocument
import io.renku.search.solr.documents.Project as ProjectDocument
import io.renku.search.solr.documents.User as UserDocument
import io.renku.search.provision.events.syntax.*
import io.renku.solr.client.DocVersion

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

  given DocumentMerger[v1.ProjectAuthorizationAdded] =
    instance[v1.ProjectAuthorizationAdded](
      _.toModel(DocVersion.NotExists).some
    )((paa, existing) =>
      existing match
        case p: PartialEntityDocument.Project =>
          p.applyTo(paa.toModel(p.version)).some
        case p: EntityDocument =>
          paa.toModel(p.version).applyTo(p).some
    )

  given DocumentMerger[v1.ProjectAuthorizationUpdated] =
    instance[v1.ProjectAuthorizationUpdated](
      _.toModel(DocVersion.NotExists).some
    )((pau, existing) =>
      existing match
        case p: PartialEntityDocument.Project =>
          p.remove(pau.userId.toId).applyTo(pau.toModel(p.version)).some
        case p: ProjectDocument =>
          pau.toModel(p.version).applyTo(p.removeMember(pau.userId.toId)).some
        case _ => None
    )

  given DocumentMerger[v1.ProjectAuthorizationRemoved] =
    instance[v1.ProjectAuthorizationRemoved](_ => None)((par, existing) =>
      existing match
        case p: PartialEntityDocument.Project =>
          p.remove(par.userId.to[Id]).some
        case p: ProjectDocument =>
          p.removeMember(par.userId.to[Id]).some

        case _ => None
    )

  given v1Merge: DocumentMerger[v1.ProjectCreated] =
    def convert(pc: v1.ProjectCreated): ProjectDocument =
      pc.toModel(DocVersion.NotExists)

    instance[v1.ProjectCreated](convert(_).some)((pc, existing) =>
      existing match
        case p: PartialEntityDocument.Project =>
          p.applyTo(convert(pc)).some
        case p: ProjectDocument =>
          // already exists, but we overwrite
          Some(
            convert(pc)
              .setVersion(p.version)
              .copy(owners = p.owners, members = p.members)
          )
        case _: UserDocument =>
          None
    )

  given v2Merge: DocumentMerger[v2.ProjectCreated] =
    def convert(pc: v2.ProjectCreated): ProjectDocument =
      pc.toModel(DocVersion.NotExists)

    instance[v2.ProjectCreated](convert(_).some) { (pc, existing) =>
      existing match
        case p: PartialEntityDocument.Project =>
          p.applyTo(convert(pc)).some

        case p: ProjectDocument =>
          Some(
            convert(pc)
              .setVersion(p.version)
              .copy(owners = p.owners, members = p.members)
          )

        case _: UserDocument =>
          None
    }

  given DocumentMerger[v1.ProjectUpdated] =
    instance[v1.ProjectUpdated](_.toModel(DocVersion.NotExists).some)((pu, existing) =>
      existing match
        case p: PartialEntityDocument.Project =>
          pu.toModel(p).some
        case orig: ProjectDocument =>
          pu.toModel(orig).some
        case _ => None
    )

  given DocumentMerger[v1.UserAdded] =
    def convert(ua: v1.UserAdded): UserDocument =
      ua.toModel(DocVersion.NotExists)

    instance[v1.UserAdded](convert(_).some)((ua, existing) =>
      existing match
        case u: EntityDocument        => Some(convert(ua).setVersion(u.version))
        case _: PartialEntityDocument => None
    )

  given DocumentMerger[v1.UserUpdated] =
    instance[v1.UserUpdated](_ => None)((uu, existing) =>
      existing match
        case orig: UserDocument =>
          uu.toModel(orig).some
        case _ => None
    )
