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

import io.bullet.borer.Decoder
import io.bullet.borer.Encoder

/** A solr version as described in [optimistic
  * locking](https://solr.apache.org/guide/solr/latest/indexing-guide/partial-document-updates.html#optimistic-concurrency)
  */
enum DocVersion:
  case Exact(version: Long)
  case Exists
  case NotExists
  case Off

  lazy val asLong: Long = this match
    case Exact(n)  => n
    case Exists    => 1
    case NotExists => -1
    case Off       => 0

object DocVersion:
  given Decoder[DocVersion] =
    Decoder.forLong.map(fromLong)

  given Encoder[DocVersion] =
    Encoder.forLong.contramap(_.asLong)

  def fromLong(version: Long): DocVersion =
    version match
      case _ if version > 1  => Exact(version)
      case _ if version == 1 => Exists
      case _ if version < 0  => NotExists
      case _                 => Off
