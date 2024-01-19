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

package io.renku.redis.client

import dev.profunktor.redis4cats.data.RedisCodec
import io.lettuce.core.codec.{ByteArrayCodec, RedisCodec as JRedisCodec, StringCodec}
import scodec.bits.ByteVector

import java.nio.ByteBuffer

object StringBytesCodec:

  val instance: RedisCodec[String, ByteVector] = RedisCodec {
    new JRedisCodec[String, ByteVector] {

      override def decodeKey(bytes: ByteBuffer): String =
        StringCodec.UTF8.decodeKey(bytes)

      override def decodeValue(bytes: ByteBuffer): ByteVector =
        ByteVector.view(ByteArrayCodec.INSTANCE.decodeValue(bytes))

      override def encodeKey(key: String): ByteBuffer =
        StringCodec.UTF8.encodeKey(key)

      override def encodeValue(value: ByteVector): ByteBuffer =
        ByteArrayCodec.INSTANCE.encodeValue(value.toArray)
    }
  }
