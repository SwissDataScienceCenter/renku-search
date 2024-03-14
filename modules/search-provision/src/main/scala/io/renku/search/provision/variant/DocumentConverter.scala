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

package io.renku.search.provision.variant

import io.renku.search.solr.documents.Entity as Document //TODO rename
import io.renku.search.solr.documents.Project as ProjectDocument
import io.renku.search.solr.documents.User as UserDocument
import io.github.arainko.ducktape.*
import io.renku.events.v1.*
import io.renku.search.model.Id
import io.renku.search.provision.TypeTransformers.given

trait DocumentConverter[A]:
  def convert(a: A): Document

object DocumentConverter:
  def create[A](f: A => Document): DocumentConverter[A] =
    (a: A) => f(a)

  def fromTransformer[A](t: Transformer[A, Document]): DocumentConverter[A] =
    create(t.transform)

  def apply[A](using d: DocumentConverter[A]): DocumentConverter[A] = d

  given DocumentConverter[ProjectCreated] =
    fromTransformer(
      _.into[ProjectDocument].transform(
        Field.computed(_.owners, pc => List(Id(pc.createdBy))),
        Field.default(_.members),
        Field.default(_.score)
      )
    )

  given DocumentConverter[UserAdded] =
    fromTransformer(
      _.into[UserDocument].transform(
        Field.default(_.score),
        Field.computed(_.name, u => UserDocument.nameFrom(u.firstName, u.lastName)),
        Field.default(_.visibility)
      )
    )

extension [A: DocumentConverter](self: A)
  def toDocument = DocumentConverter[A].convert(self)
