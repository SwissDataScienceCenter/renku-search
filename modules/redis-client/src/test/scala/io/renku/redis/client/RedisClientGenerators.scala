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

import org.scalacheck.Gen
import org.scalacheck.Gen.{alphaLowerChar, alphaNumChar}

object RedisClientGenerators:

  private val stringGen: Gen[String] =
    Gen
      .chooseNum(3, 10)
      .flatMap(Gen.stringOfN(_, alphaLowerChar))

  val queueNameGen: Gen[QueueName] =
    stringGen.map(QueueName(_))

  val clientIdGen: Gen[ClientId] =
    Gen
      .chooseNum(3, 10)
      .flatMap(Gen.stringOfN(_, alphaNumChar).map(ClientId(_)))

  val messageIdGen: Gen[MessageId] =
    for
      part1 <- Gen.chooseNum(3, 10)
      part2 <- Gen.chooseNum(3, 10)
    yield s"$part1.$part2"
