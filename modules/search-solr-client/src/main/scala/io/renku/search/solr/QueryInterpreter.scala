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

package io.renku.search.solr.client

import io.renku.search.query.Query
import io.renku.search.query.Query.Segment
import io.renku.search.query.FieldTerm
import io.renku.search.solr.schema.EntityDocumentSchema.Fields
import org.apache.lucene.queryparser.flexible.standard.QueryParserUtil

object QueryInterpreter {

  def apply(query: Query): String =
    query.segments
      .map {
        case Segment.Field(FieldTerm.ProjectIdIs(ids)) =>
          ids.toList.map(escape).map(id => s"${Fields.id.name}:$id").mkString("(", " OR ", ")")

        case Segment.Field(FieldTerm.SlugIs(slugs)) =>
          slugs.toList.map(escape).map(slug => s"${Fields.slug.name}:$slug").mkString("(", " OR ", ")")

        case Segment.Field(FieldTerm.NameIs(names)) =>
          names.toList.map(escape).map(name => s"${Fields.name.name}:$name").mkString("(", " OR ", ")")

        case Segment.Text(txt) =>
          s"${Fields.contentAll.name}:${escape(txt)}"

        case _ =>
          ""
      }
      .mkString(" AND ")

  private def escape(s: String): String =
    val escaped = QueryParserUtil.escape(s)
    if (escaped.exists(_.isWhitespace)) s"($escaped)"
    else escaped

}
