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

import cats.data.NonEmptyList

enum SchemaVersion:
  case V1
  case V2

  lazy val name: String = productPrefix

object SchemaVersion:
  val all: NonEmptyList[SchemaVersion] =
    NonEmptyList.fromListUnsafe(SchemaVersion.values.toList)

  // the avro schema defines the version to be a string, not an enum
  // we try a few values that would make sense here
  private val candidateValues = all.toList.map { v =>
    v -> Set(v.name, v.name.toLowerCase(), v.name.drop(1))
  }.toMap

  def fromString(s: String): Either[String, SchemaVersion] =
    candidateValues
      .find(_._2.contains(s))
      .map(_._1)
      .toRight(s"Invalid schema version: $s")
