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

final case class RedisConfig(
    host: RedisHost,
    port: RedisPort,
    maybeDB: Option[RedisDB],
    maybePassword: Option[RedisPassword] = None,
    maybeMasterSet: Option[RedisMasterSet] = None
)

opaque type RedisHost = String
object RedisHost {
  def apply(v: String): RedisHost = v
  extension (self: RedisHost) def value: String = self
}

opaque type RedisPort = Int
object RedisPort {
  def apply(v: Int): RedisPort = v
  extension (self: RedisPort)
    def value: Int = self
    def plusOne: RedisPort = self + 1
}

opaque type RedisDB = Int
object RedisDB {
  def apply(v: Int): RedisDB = v
  extension (self: RedisDB) def value: Int = self
}

opaque type RedisPassword = String
object RedisPassword {
  def apply(v: String): RedisPassword = v
  extension (self: RedisPassword) def value: String = self
}

opaque type RedisMasterSet = String
object RedisMasterSet {
  def apply(v: String): RedisMasterSet = v
  extension (self: RedisMasterSet) def value: String = self
}
