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

import scala.collection.mutable
import scala.jdk.CollectionConverters.given

import cats.MonadThrow
import cats.effect.{Async, Resource}
import cats.syntax.all.*

import dev.profunktor.redis4cats.connection.{RedisClient, RedisURI}
import dev.profunktor.redis4cats.effect.Log
import io.lettuce.core.RedisURI as JRedisURI
import io.lettuce.core.sentinel.api.StatefulRedisSentinelConnection

sealed private trait ClientCreator[F[_]]:
  def makeClient: Resource[F, RedisClient]

private object ClientCreator:

  def apply[F[_]: Async: Log](cfg: RedisConfig): ClientCreator[F] =
    if cfg.sentinel then new SentinelClientCreator(cfg)
    else new SingleNodeClientCreator(cfg)

private class SingleNodeClientCreator[F[_]: Async: Log](cfg: RedisConfig)
    extends ClientCreator[F]:

  override def makeClient: Resource[F, RedisClient] =
    RedisClient[F].fromUri(cfg.asRedisUri)

private class SentinelClientCreator[F[_]: Async: Log](cfg: RedisConfig)
    extends ClientCreator[F]:

  override def makeClient: Resource[F, RedisClient] =
    makeSentinelClient >>= makeMasterNodeClient

  private def makeSentinelClient =
    RedisClient[F].fromUri(cfg.asRedisUri)

  private def makeMasterNodeClient(sentinelRedisClient: RedisClient) =
    Resource
      .eval(connectSentinel(sentinelRedisClient) >>= findMasterNodeUri)
      .flatMap(RedisClient[F].fromUri(_))

  private def connectSentinel(
      client: RedisClient
  ): F[StatefulRedisSentinelConnection[String, String]] =
    MonadThrow[F].catchNonFatal(
      client.underlying.connectSentinel()
    )

  private def findMasterNodeUri(conn: StatefulRedisSentinelConnection[String, String]) =
    findMasterSet >>= (findMasterNodeInfo(conn, _)) >>= findNodeUri

  private def findMasterSet: F[RedisMasterSet] =
    MonadThrow[F].fromOption(
      cfg.maybeMasterSet,
      new Exception("No Redis MasterSet configured")
    )

  private def findMasterNodeInfo(
      conn: StatefulRedisSentinelConnection[String, String],
      masterSet: RedisMasterSet
  ): F[mutable.Map[String, String]] =
    MonadThrow[F].catchNonFatal(
      conn.sync().master(masterSet.value).asScala
    )

  private def findNodeUri(
      nodeInfo: mutable.Map[String, String]
  ): F[RedisURI] =
    MonadThrow[F].fromOption(
      (nodeInfo.get("ip") -> nodeInfo.get("port"))
        .mapN { (host, port) =>
          val builder = JRedisURI.Builder.redis(host, port.toInt)
          cfg.maybePassword.map(_.value.toCharArray).fold(builder)(builder.withPassword)
          cfg.maybeDB.map(_.value).fold(builder)(builder.withDatabase)
          RedisURI.fromUnderlying(builder.build())
        },
      new Exception("No Redis Master node URI found")
    )
