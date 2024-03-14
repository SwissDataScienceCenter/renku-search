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

import fs2.Pipe

import io.renku.search.solr.documents.Entity as Document
import fs2.Chunk

trait ConvertDocument[F[_]]:
  def convert[A](using
      c: DocumentConverter[A]
  ): Pipe[F, MessageReader.Message[A], MessageReader.Message[Document]]

  def convertChunk[A](using
      c: DocumentConverter[A]
  ): Pipe[F, Chunk[MessageReader.Message[A]], Chunk[MessageReader.Message[Document]]]

object ConvertDocument:
  /** Converts input messages into solr document values. */
  def apply[F[_]]: ConvertDocument[F] =
    new ConvertDocument[F]:
      def convert[A](using
          c: DocumentConverter[A]
      ): Pipe[F, MessageReader.Message[A], MessageReader.Message[Document]] =
        _.map(m => m.map(c.convert))

      def convertChunk[A](using
        c: DocumentConverter[A]
      ): Pipe[F, Chunk[MessageReader.Message[A]], Chunk[MessageReader.Message[Document]]] =
        _.map(_.map(_.map(c.convert)))
