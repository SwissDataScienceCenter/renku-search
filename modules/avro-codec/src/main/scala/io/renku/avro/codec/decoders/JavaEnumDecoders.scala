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

package io.renku.avro.codec.decoders

import io.renku.avro.codec.AvroDecoder
import org.apache.avro.generic.GenericEnumSymbol

import scala.reflect.ClassTag

trait JavaEnumDecoders {

  given [E <: Enum[E]](using ctag: ClassTag[E]): AvroDecoder[E] = AvroDecoder.basic {
    case e: Enum[?] => e.asInstanceOf[E]
    case e: GenericEnumSymbol[?] =>
      Enum.valueOf[E](ctag.runtimeClass.asInstanceOf[Class[E]], e.toString)
  }
}
