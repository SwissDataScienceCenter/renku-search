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

import io.bullet.borer.Decoder
import io.bullet.borer.Encoder

enum KeyUse(val name: String):
  case Sign extends KeyUse("sig")
  case Encrypt extends KeyUse("enc")

object KeyUse:
  def fromString(s: String): Either[String, KeyUse] =
    KeyUse.values.find(_.name.equalsIgnoreCase(s)).toRight(s"Invalid key use: $s")

  given Decoder[KeyUse] = Decoder.forString.mapEither(fromString)
  given Encoder[KeyUse] = Encoder.forString.contramap(_.name)
