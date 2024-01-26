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

package io.renku.solr.client.util

import cats.effect.IO
import cats.syntax.all.*
import io.renku.search.http.ResponseLogging
import io.renku.solr.client.schema.{FieldName, SchemaCommand, TypeName}
import io.renku.solr.client.{QueryString, SolrClient}

trait SolrTruncate {

  def truncateAll(
      client: SolrClient[IO]
  )(fields: Seq[FieldName], types: Seq[TypeName]): IO[Unit] =
    for {
      _ <- client.delete(QueryString("*:*"))
      _ <- fields
        .map(SchemaCommand.DeleteField.apply)
        .traverse_(modifyIgnoreError(client))
      _ <- types
        .map(SchemaCommand.DeleteType.apply)
        .traverse_(modifyIgnoreError(client))
    } yield ()

  private def modifyIgnoreError(client: SolrClient[IO])(c: SchemaCommand) =
    client
      .modifySchema(Seq(c), ResponseLogging.Ignore)
      .attempt
      .void
}
