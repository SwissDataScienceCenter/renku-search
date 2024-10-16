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

import io.renku.search.events.EventMessage
import io.renku.search.model.{Id, Namespace}
import io.renku.search.solr.documents.Group as GroupDocument
import io.renku.search.solr.documents.Project as ProjectDocument

final case class EntityOrPartialMessage[A: IdExtractor](
    message: EventMessage[A],
    documents: Map[Id, EntityOrPartial]
):
  def merge(
      ifEmpty: A => Option[EntityOrPartial],
      ifMerge: (A, EntityOrPartial) => Option[EntityOrPartial]
  ): EventMessage[EntityOrPartial] =
    EventMessage(
      message.id,
      message.header,
      message.payloadSchema,
      message.payload.flatMap { a =>
        documents
          .get(IdExtractor[A].getId(a))
          .map(doc => ifMerge(a, doc))
          .getOrElse(ifEmpty(a))
      }
    )

  def findPayloadById(id: Id): Option[A] =
    message.payload.find(e => IdExtractor[A].getId(e) == id)

  def findGroupByNs(ns: Namespace): Option[GroupDocument] =
    documents.values.collect {
      case g: GroupDocument if g.namespace == ns => g
    }.headOption

  def getIds: Set[Id] =
    if (documents.isEmpty) message.payload.map(IdExtractor[A].getId).toSet
    else documents.keySet

  def mapToMessage(
      f: EntityOrPartial => Option[EntityOrPartial]
  ): EventMessage[EntityOrPartial] =
    EventMessage(
      message.id,
      message.header,
      message.payloadSchema,
      documents.values.toSeq.flatMap(f)
    )

  lazy val asMessage: EventMessage[EntityOrPartial] =
    EventMessage(
      message.id,
      message.header,
      message.payloadSchema,
      documents.values.toSeq
    )

  def appendDocuments(docs: List[EntityOrPartial]): EntityOrPartialMessage[A] =
    EntityOrPartialMessage(message, documents ++ docs.map(d => d.id -> d).toMap)

  def setDocuments(docs: List[EntityOrPartial]): EntityOrPartialMessage[A] =
    EntityOrPartialMessage(message, docs.map(d => d.id -> d).toMap)

  def getGroups: List[GroupDocument] =
    documents.values.collect { case g: GroupDocument =>
      g
    }.toList

  def getProjectsByGroup(g: GroupDocument): List[ProjectDocument] =
    documents.values.collect {
      case p: ProjectDocument if p.namespace == g.namespace.some => p
    }.toList

object EntityOrPartialMessage:
  def noDocuments[A](em: EventMessage[A])(using
      IdExtractor[A]
  ): EntityOrPartialMessage[A] = EntityOrPartialMessage(em, Map.empty)
