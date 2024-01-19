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

package io.renku.avro.codec.encoders

import io.renku.avro.codec.AvroEncoder

trait PrimitiveEncoders:
  given AvroEncoder[Long] = AvroEncoder.basic(n => java.lang.Long.valueOf(n))
  given AvroEncoder[Int] = AvroEncoder.basic(n => java.lang.Integer.valueOf(n))
  given AvroEncoder[Short] = AvroEncoder.basic(n => java.lang.Short.valueOf(n))
  given AvroEncoder[Byte] = AvroEncoder.basic(n => java.lang.Byte.valueOf(n))
  given AvroEncoder[Double] = AvroEncoder.basic(n => java.lang.Double.valueOf(n))
  given AvroEncoder[Float] = AvroEncoder.basic(n => java.lang.Float.valueOf(n))
  given AvroEncoder[Boolean] = AvroEncoder.basic(n => java.lang.Boolean.valueOf(n))
