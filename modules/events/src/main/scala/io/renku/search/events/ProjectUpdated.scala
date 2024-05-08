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

package io.renku.search.events

import cats.Show
import cats.data.NonEmptyList

import io.renku.avro.codec.AvroEncoder
import io.renku.avro.codec.all.given
import io.renku.events.{v1, v2}
import io.renku.search.model.*
import io.renku.search.model.projects.Visibility
import org.apache.avro.Schema

sealed trait ProjectUpdated extends RenkuEventPayload:
  def fold[A](fv1: v1.ProjectUpdated => A, fv2: v2.ProjectUpdated => A): A
  def withId(id: Id): ProjectUpdated
  def withNamespace(ns: Namespace): ProjectUpdated
  def namespace: Option[Namespace]
  def version: NonEmptyList[SchemaVersion] =
    NonEmptyList.of(fold(_ => SchemaVersion.V1, _ => SchemaVersion.V2))
  def schema: Schema =
    fold(_ => v1.ProjectUpdated.SCHEMA$, _ => v2.ProjectUpdated.SCHEMA$)

object ProjectUpdated:
  def apply(
      id: Id,
      name: Name,
      namespace: Namespace,
      slug: projects.Slug,
      visibility: projects.Visibility,
      repositories: Seq[projects.Repository] = Seq(),
      description: Option[projects.Description] = None,
      keywords: Seq[Keyword] = Seq()
  ): ProjectUpdated = V2(
    v2.ProjectUpdated(
      id.value,
      name.value,
      namespace.value,
      slug.value,
      repositories.map(_.value),
      visibility match
        case Visibility.Private => v2.Visibility.PRIVATE
        case Visibility.Public  => v2.Visibility.PUBLIC
      ,
      description.map(_.value),
      keywords.map(_.value)
    )
  )

  final case class V1(event: v1.ProjectUpdated) extends ProjectUpdated:
    val id: Id = Id(event.id)
    def withId(id: Id): ProjectUpdated = V1(event.copy(id = id.value))
    val namespace: Option[Namespace] = None
    def withNamespace(ns: Namespace): ProjectUpdated = V2(
      v2.ProjectUpdated(
        event.id,
        event.name,
        ns.value,
        event.slug,
        event.repositories,
        v2.Visibility.valueOf(event.visibility.name),
        event.description,
        event.keywords
      )
    )
    def fold[A](fv1: v1.ProjectUpdated => A, fv2: v2.ProjectUpdated => A): A = fv1(event)

  final case class V2(event: v2.ProjectUpdated) extends ProjectUpdated:
    val id: Id = Id(event.id)
    def withId(id: Id): ProjectUpdated = V2(event.copy(id = id.value))
    def withNamespace(ns: Namespace): ProjectUpdated = V2(
      event.copy(namespace = ns.value)
    )
    val namespace: Option[Namespace] = Some(Namespace(event.namespace))
    def fold[A](fv1: v1.ProjectUpdated => A, fv2: v2.ProjectUpdated => A): A = fv2(event)

  given AvroEncoder[ProjectUpdated] =
    val v1e = AvroEncoder[v1.ProjectUpdated]
    val v2e = AvroEncoder[v2.ProjectUpdated]
    AvroEncoder.basic { v =>
      v.fold(v1e.encode(v.schema), v2e.encode(v.schema))
    }

  given EventMessageDecoder[ProjectUpdated] =
    EventMessageDecoder.instance { qm =>
      qm.header.schemaVersion match
        case SchemaVersion.V1 =>
          val schema = v1.ProjectUpdated.SCHEMA$
          qm.toMessage[v1.ProjectUpdated](schema)
            .map(_.map(ProjectUpdated.V1.apply))

        case SchemaVersion.V2 =>
          val schema = v2.ProjectUpdated.SCHEMA$
          qm.toMessage[v2.ProjectUpdated](schema)
            .map(_.map(ProjectUpdated.V2.apply))
    }

  given Show[ProjectUpdated] =
    Show.show(v => s"slug '${v.fold(_.slug, _.slug)}'")
