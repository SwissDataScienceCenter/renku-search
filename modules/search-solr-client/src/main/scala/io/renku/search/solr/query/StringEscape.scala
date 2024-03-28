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

package io.renku.search.solr.query

private[query] object StringEscape:

  // Escapes query characters for solr. This is taken from here:
  // https://github.com/apache/solr/blob/bcb9f144974ed07aa3b66766302474542067b522/solr/solrj/src/java/org/apache/solr/client/solrj/util/ClientUtils.java#L163
  // to not introduce too many dependencies only for this little function
  private val defaultSpecialChars = "\\+-!():^[]\"{}~*?|&;/"

  def escape(s: String, specialChars: String): String =
    inline def isSpecial(c: Char) = c.isWhitespace || specialChars.contains(c)
    val sb = new StringBuilder();
    s.foreach { c =>
      if (isSpecial(c)) sb.append('\\')
      sb.append(c)
    }
    sb.toString

  def queryChars(s: String): String = escape(s, defaultSpecialChars)
