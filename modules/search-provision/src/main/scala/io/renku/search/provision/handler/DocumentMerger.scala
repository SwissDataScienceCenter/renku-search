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

import io.renku.search.events.*
import io.renku.search.provision.events.syntax.*
import io.renku.search.solr.documents.Group as GroupDocument
import io.renku.search.solr.documents.PartialEntityDocument
import io.renku.search.solr.documents.Project as ProjectDocument
import io.renku.search.solr.documents.User as UserDocument
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
      def create(value: A): Option[EntityOrPartial] =
        onCreate(value)
      def merge(value: A, existing: EntityOrPartial): Option[EntityOrPartial] =
        onMerge(value, existing)
    }

  given DocumentMerger[ProjectMemberAdded] =
    instance[ProjectMemberAdded](_.toModel(DocVersion.NotExists).some) {
      (paa, existing) =>
        existing match
          case p: PartialEntityDocument.Project =>
            p.modifyEntityMembers(_.addMember(paa.userId, paa.role)).some
          case p: ProjectDocument =>
            p.modifyEntityMembers(_.addMember(paa.userId, paa.role)).some
          case _: PartialEntityDocument.Group | _: GroupDocument | _: UserDocument => None
    }

  given DocumentMerger[ProjectMemberUpdated] =
    instance[ProjectMemberUpdated](_.toModel(DocVersion.NotExists).some) {
      (pmu, existing) =>
        existing match
          case p: PartialEntityDocument.Project =>
            p.modifyEntityMembers(_.addMember(pmu.userId, pmu.role)).some
          case p: ProjectDocument =>
            p.modifyEntityMembers(_.addMember(pmu.userId, pmu.role)).some
          case _ => None
    }

  given DocumentMerger[ProjectMemberRemoved] =
    instance[ProjectMemberRemoved](_ => None) { (pmr, existing) =>
      existing match
        case p: PartialEntityDocument.Project =>
          p.modifyEntityMembers(_.removeMember(pmr.userId)).some
        case p: ProjectDocument =>
          p.modifyEntityMembers(_.removeMember(pmr.userId)).some
        case _: UserDocument | _: GroupDocument | _: PartialEntityDocument.Group =>
          None
    }

  given DocumentMerger[ProjectCreated] =
    instance[ProjectCreated](_.toModel(DocVersion.NotExists).some) { (pc, existing) =>
      existing match
        case p: PartialEntityDocument.Project =>
          p.applyTo(pc.toModel(p.version)).some
        case p: ProjectDocument =>
          pc.toModel(p.version).modifyEntityMembers(m => p.toEntityMembers ++ m).some
        case _: UserDocument | _: GroupDocument | _: PartialEntityDocument.Group =>
          None
    }

  given DocumentMerger[ProjectUpdated] =
    instance[ProjectUpdated](_.toModel(DocVersion.NotExists).some) { (pu, existing) =>
      existing match
        case p: PartialEntityDocument.Project =>
          pu.toModel(p).some
        case orig: ProjectDocument =>
          pu.toModel(orig).some
        case _: UserDocument | _: GroupDocument | _: PartialEntityDocument.Group =>
          None
    }

  given DocumentMerger[UserAdded] =
    instance[UserAdded](_.toModel(DocVersion.NotExists).some) { (ua, existing) =>
      existing match
        case u: UserDocument => ua.toModel(u.version).some
        case _               => None
    }

  given DocumentMerger[UserUpdated] =
    instance[UserUpdated](_ => None) { (uu, existing) =>
      existing match
        case orig: UserDocument =>
          uu.toModel(orig).some
        case _ => None
    }

  given DocumentMerger[GroupAdded] =
    instance[GroupAdded](_.toModel(DocVersion.NotExists).some) {
      case (ga, p: PartialEntityDocument.Group) =>
        p.applyTo(ga.toModel(p.version)).some
      case (ga, u: GroupDocument) =>
        ga.toModel(u.version).some
      case _ => None
    }

  given DocumentMerger[GroupUpdated] =
    instance[GroupUpdated](_.toModel(DocVersion.NotExists).some) {
      case (gu, g: PartialEntityDocument.Group) =>
        gu.toModel(g).some
      case (gu, g: GroupDocument) =>
        gu.toModel(g).some
      case _ => None
    }

  given DocumentMerger[GroupMemberAdded] =
    instance[GroupMemberAdded](_.toModel(DocVersion.NotExists).some) { (gma, existing) =>
      existing match
        case g: PartialEntityDocument.Group =>
          g.modifyEntityMembers(_.addMember(gma.userId, gma.role)).some
        case g: GroupDocument =>
          g.modifyEntityMembers(_.addMember(gma.userId, gma.role)).some
        case _: PartialEntityDocument.Project | _: UserDocument | _: ProjectDocument =>
          None
    }

  given DocumentMerger[GroupMemberUpdated] =
    instance[GroupMemberUpdated](_.toModel(DocVersion.NotExists).some) {
      (gmu, existing) =>
        existing match
          case g: PartialEntityDocument.Group =>
            g.modifyEntityMembers(_.addMember(gmu.userId, gmu.role)).some
          case g: GroupDocument =>
            g.modifyEntityMembers(_.addMember(gmu.userId, gmu.role)).some
          case _: PartialEntityDocument.Project | _: UserDocument | _: ProjectDocument =>
            None
    }

  given DocumentMerger[GroupMemberRemoved] =
    instance[GroupMemberRemoved](_ => None) { (gmr, existing) =>
      existing match
        case p: PartialEntityDocument.Group =>
          p.modifyEntityMembers(_.removeMember(gmr.userId)).some
        case p: GroupDocument =>
          p.modifyEntityMembers(_.removeMember(gmr.userId)).some
        case _: UserDocument | _: ProjectDocument | _: PartialEntityDocument.Project =>
          None
    }
