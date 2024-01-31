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

package io.renku.queue.client

import scodec.bits.ByteVector

final case class Message(id: MessageId, encoding: Encoding, payload: ByteVector)

final case class MessageId(value: String) extends AnyVal

sealed trait Encoding extends Product:
  lazy val name: String = productPrefix

object Encoding:

  val all: Set[Encoding] = Set(Binary, Json)

  def from(v: String): Either[IllegalArgumentException, Encoding] =
    all
      .find(_.productPrefix.equalsIgnoreCase(v))
      .toRight(new IllegalArgumentException(s"'$v' not a valid payload Encoding"))

  case object Binary extends Encoding
  case object Json extends Encoding
