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

package io.renku.search.config

import cats.syntax.all.*
import ciris.{ConfigDecoder, ConfigError}
import com.comcast.ip4s.{Ipv4Address, Port}
import io.renku.redis.client.*
import org.http4s.Uri

import scala.concurrent.duration.{Duration, FiniteDuration}
import io.renku.search.common.UrlPattern

trait ConfigDecoders:

  given ConfigDecoder[String, Uri] =
    ConfigDecoder[String].mapEither { (_, s) =>
      Uri.fromString(s).leftMap(err => ConfigError(err.getMessage))
    }

  given ConfigDecoder[String, FiniteDuration] =
    ConfigDecoder[String].mapOption("duration") { s =>
      Duration.unapply(s).map(Duration.apply.tupled).filter(_.isFinite)
    }

  given ConfigDecoder[String, RedisHost] =
    ConfigDecoder[String].map(RedisHost.apply)
  given ConfigDecoder[String, RedisPort] =
    ConfigDecoder[String, Int].map(RedisPort.apply)
  given ConfigDecoder[String, RedisDB] =
    ConfigDecoder[String, Int].map(RedisDB.apply)
  given ConfigDecoder[String, RedisPassword] =
    ConfigDecoder[String].map(RedisPassword.apply)
  given ConfigDecoder[String, RedisMasterSet] =
    ConfigDecoder[String].map(RedisMasterSet.apply)

  given ConfigDecoder[String, QueueName] =
    ConfigDecoder[String].map(s => QueueName(s))
  given ConfigDecoder[String, ClientId] =
    ConfigDecoder[String].map(s => ClientId(s))

  given ConfigDecoder[String, Ipv4Address] =
    ConfigDecoder[String]
      .mapOption(Ipv4Address.getClass.getSimpleName)(Ipv4Address.fromString)
  given ConfigDecoder[String, Port] =
    ConfigDecoder[String]
      .mapOption(Port.getClass.getSimpleName)(Port.fromString)

  given ConfigDecoder[String, List[UrlPattern]] =
    ConfigDecoder[String].map { str =>
      str.split(',').toList.map(UrlPattern.fromString)
    }
