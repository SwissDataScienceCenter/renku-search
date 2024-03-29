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
import io.renku.search.provision.handler.TypeTransformers.given
import io.renku.search.solr.documents.EntityDocument
import io.renku.search.solr.documents.Project as ProjectDocument
import io.renku.search.solr.documents.User as UserDocument

object DocumentUpdates:

  def project(update: ProjectUpdated, orig: EntityDocument): Option[EntityDocument] =
    orig match
      case orig: ProjectDocument =>
        update
          .into[ProjectDocument]
          .transform(
            Field.const(_.createdBy, orig.createdBy),
            Field.const(_.creationDate, orig.creationDate),
            Field.const(_.owners, orig.owners),
            Field.const(_.members, orig.members),
            Field.default(_.score)
          )
          .some
      case _ => None // todo really throw here?

  def user(update: UserUpdated, orig: EntityDocument): Option[EntityDocument] = orig match
    case _: UserDocument =>
      update
        .into[UserDocument]
        .transform(
          Field.default(_.score),
          Field.computed(_.name, u => UserDocument.nameFrom(u.firstName, u.lastName))
        )
        .some
    case _ => None
