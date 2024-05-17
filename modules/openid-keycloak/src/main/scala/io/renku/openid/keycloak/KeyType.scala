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

package io.renku.openid.keycloak

import io.bullet.borer.{Decoder, Encoder}

enum KeyType:
  case EC
  case RSA
  case OKP

  def name: String = productPrefix.toUpperCase

object KeyType:
  def fromString(s: String): Either[String, KeyType] =
    KeyType.values.find(_.name.equalsIgnoreCase(s)).toRight(s"Invalid key type: $s")

  given Decoder[KeyType] = Decoder.forString.mapEither(fromString)
  given Encoder[KeyType] = Encoder.forString.contramap(_.name)
