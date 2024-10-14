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

package io.renku.search.api.data

import scala.util.{Success, Try}

import io.renku.search.solr.documents.{
  Group as GroupDocument,
  Project as ProjectDocument,
  User as UserDocument,
  *
}

trait EntityConverter:
  def project(p: ProjectDocument): Try[SearchEntity.Project] =
    Try(
      SearchEntity.Project(
        id = p.id,
        name = p.name,
        slug = p.slug,
        namespace = p.namespaceDetails.flatMap(_.docs.headOption) match {
          case Some(u: UserDocument)  => user(u).get
          case Some(g: GroupDocument) => group(g)
          case _ =>
            scribe.error(
              s"Error converting project namespace due to incorrect data. A user or group was expected as the project namespace of ${p.id}/${p.slug}, but found nothing"
            )
            throw new Exception(
              s"Missing namespace for project with id ${p.id} and slug ${p.slug}"
            )
        },
        repositories = p.repositories,
        visibility = p.visibility,
        description = p.description,
        createdBy =
          p.creatorDetails.flatMap(_.docs.headOption).flatMap(u => Some(user(u).get)),
        creationDate = p.creationDate,
        keywords = p.keywords,
        score = p.score
      )
    )

  def user(u: UserDocument): Try[SearchEntity.User] =
    Try(
      SearchEntity.User(
        id = u.id,
        namespace = u.namespace match {
          case Some(x) => x
          case None =>
            scribe.error(s"Missing namespace for user with id ${u.id}")
            throw new Exception(s"Missing namespace for user with id ${u.id}")
        },
        firstName = u.firstName,
        lastName = u.lastName,
        score = u.score
      )
    )

  def group(g: GroupDocument): SearchEntity.Group =
    SearchEntity.Group(
      id = g.id,
      name = g.name,
      namespace = g.namespace,
      description = g.description,
      score = g.score
    )

  def entity(e: EntityDocument): Try[SearchEntity] = e match
    case p: ProjectDocument => project(p)
    case u: UserDocument    => user(u)
    case g: GroupDocument   => Success(group(g))
end EntityConverter

object EntityConverter extends EntityConverter:
  def apply(e: EntityDocument): Try[SearchEntity] = entity(e)
