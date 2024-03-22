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
import io.renku.search.model.Id
import io.renku.search.provision.TypeTransformers.given
import io.renku.search.solr.documents.EntityDocument
import io.renku.search.solr.documents.PartialEntityDocument
import io.renku.search.solr.documents.Project as ProjectDocument

trait DocumentMerger[A]:
  def create(value: A): Option[EntityOrPartial]
  def merge(value: A, existing: EntityOrPartial): Option[EntityOrPartial]

object DocumentMerger:
  def apply[A](using dm: DocumentMerger[A]): DocumentMerger[A] = dm

  def create[A](
      onCreate: A => Option[EntityOrPartial]
  )(onMerge: (A, EntityOrPartial) => Option[EntityOrPartial]): DocumentMerger[A] =
    new DocumentMerger[A] {
      def create(value: A) = onCreate(value)
      def merge(value: A, existing: EntityOrPartial) = onMerge(value, existing)
    }

  given DocumentMerger[ProjectAuthorizationAdded] =
    create[ProjectAuthorizationAdded](_.to[PartialEntityDocument].some)((paa, existing) =>
      existing match
        case p: PartialEntityDocument.Project =>
          p.applyTo(paa.to[PartialEntityDocument]).some
        case p: EntityDocument =>
          paa.to[PartialEntityDocument].applyTo(p).some
    )

  given DocumentMerger[ProjectAuthorizationUpdated] =
    create[ProjectAuthorizationUpdated](_.to[PartialEntityDocument].some)(
      (pau, existing) =>
        existing match
          case p: PartialEntityDocument.Project =>
            p.remove(pau.userId.to[Id]).applyTo(pau.to[PartialEntityDocument]).some
          case p: ProjectDocument =>
            pau.to[PartialEntityDocument].applyTo(p.removeMember(pau.userId.to[Id])).some
          case _ => None
    )

  given DocumentMerger[ProjectAuthorizationRemoved] =
    create[ProjectAuthorizationRemoved](_ => None)((par, existing) =>
      existing match
        case p: PartialEntityDocument.Project =>
          p.remove(par.userId.to[Id]).some
        case p: ProjectDocument =>
          p.removeMember(par.userId.to[Id]).some

        case _ => None
    )

  given (using conv: DocumentConverter[ProjectCreated]): DocumentMerger[ProjectCreated] =
    create[ProjectCreated](conv.convert(_).some)((pc, existing) =>
      existing match
        case p: PartialEntityDocument.Project =>
          p.applyTo(conv.convert(pc)).some
        case p: EntityDocument =>
          // already exists
          None
    )
