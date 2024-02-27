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

package io.renku.solr.client

import cats.kernel.Monoid
import io.renku.solr.client.schema.FieldName
import io.bullet.borer.Encoder

opaque type SolrSort = Seq[(FieldName, SolrSort.Direction)]

object SolrSort:
  enum Direction:
    case Asc
    case Desc
    val name: String = productPrefix.toLowerCase

  object Direction:
    def fromString(s: String): Either[String, Direction] =
      Direction.values
        .find(_.toString.equalsIgnoreCase(s))
        .toRight(s"Invalid sort direction: $s")
    def unsafeFromString(s: String): Direction =
      fromString(s).fold(sys.error, identity)

    given Encoder[Direction] = Encoder.forString.contramap(_.name)

  def apply(s: (FieldName, Direction)*): SolrSort = s

  val empty: SolrSort = Seq.empty

  extension (self: SolrSort)
    def isEmpty: Boolean = self.isEmpty
    def nonEmpty: Boolean = !self.isEmpty
    def ++(next: SolrSort): SolrSort =
      Monoid[SolrSort].combine(self, next)

  given Monoid[SolrSort] =
    Monoid.instance(empty, (a, b) => if (a.isEmpty) b else if (b.isEmpty) a else a ++ b)

  given Encoder[SolrSort] = Encoder.forString.contramap(list =>
    list.map { case (f, d) => s"${f.name} ${d.name}" }.mkString(",")
  )
