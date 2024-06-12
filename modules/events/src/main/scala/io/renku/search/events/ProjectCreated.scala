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

import io.renku.avro.codec.AvroDecoder
import io.renku.avro.codec.AvroEncoder
import io.renku.avro.codec.all.given
import io.renku.events.{v1, v2}
import io.renku.search.model.*
import org.apache.avro.Schema

sealed trait ProjectCreated extends RenkuEventPayload:
  def fold[A](fv1: v1.ProjectCreated => A, fv2: v2.ProjectCreated => A): A
  def withId(id: Id): ProjectCreated
  def withNamespace(ns: Namespace): ProjectCreated
  def namespace: Option[Namespace]
  def version: NonEmptyList[SchemaVersion] =
    NonEmptyList.of(fold(_ => SchemaVersion.V1, _ => SchemaVersion.V2))
  def schema: Schema =
    fold(_ => v1.ProjectCreated.SCHEMA$, _ => v2.ProjectCreated.SCHEMA$)

object ProjectCreated:
  def apply(
      id: Id,
      name: Name,
      namespace: Namespace,
      slug: Slug,
      visibility: Visibility,
      createdBy: Id,
      creationDate: Timestamp,
      repositories: Seq[Repository] = Seq(),
      description: Option[Description] = None,
      keywords: Seq[Keyword] = Seq()
  ): ProjectCreated = V2(
    v2.ProjectCreated(
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
      keywords.map(_.value),
      createdBy.value,
      creationDate.toInstant
    )
  )

  final case class V1(event: v1.ProjectCreated) extends ProjectCreated:
    val id: Id = Id(event.id)
    def withId(id: Id): ProjectCreated = V1(event.copy(id = id.value))
    val namespace: Option[Namespace] = None
    def withNamespace(ns: Namespace): ProjectCreated = V2(
      v2.ProjectCreated(
        event.id,
        event.name,
        ns.value,
        event.slug,
        event.repositories,
        v2.Visibility.valueOf(event.visibility.name),
        event.description,
        event.keywords,
        event.createdBy,
        event.creationDate
      )
    )
    def fold[A](fv1: v1.ProjectCreated => A, fv2: v2.ProjectCreated => A): A = fv1(event)

  final case class V2(event: v2.ProjectCreated) extends ProjectCreated:
    val id: Id = Id(event.id)
    def withId(id: Id): ProjectCreated = V2(event.copy(id = id.value))
    def withNamespace(ns: Namespace): ProjectCreated = V2(
      event.copy(namespace = ns.value)
    )
    val namespace: Option[Namespace] = Some(Namespace(event.namespace))
    def fold[A](fv1: v1.ProjectCreated => A, fv2: v2.ProjectCreated => A): A = fv2(event)

  given AvroEncoder[ProjectCreated] =
    AvroEncoder.basic { v =>
      v.fold(
        AvroEncoder[v1.ProjectCreated].encode(v.schema),
        AvroEncoder[v2.ProjectCreated].encode(v.schema)
      )
    }

  given EventMessageDecoder[ProjectCreated] =
    EventMessageDecoder.instance { qm =>
      qm.header.schemaVersion match
        case SchemaVersion.V1 =>
          val schema = v1.ProjectCreated.SCHEMA$
          qm.toMessage[v1.ProjectCreated](schema)
            .map(_.map(ProjectCreated.V1.apply))

        case SchemaVersion.V2 =>
          val schema = v2.ProjectCreated.SCHEMA$
          qm.toMessage[v2.ProjectCreated](schema)
            .map(_.map(ProjectCreated.V2.apply))
    }

  given Show[ProjectCreated] =
    Show.show(v => s"slug '${v.fold(_.slug, _.slug)}'")
