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

import cats.ApplicativeThrow
import cats.effect.{Async, Resource}
import cats.syntax.all.*
import dev.profunktor.redis4cats.connection.{RedisClient, RedisMasterReplica, RedisURI}
import dev.profunktor.redis4cats.data.{ReadFrom, RedisCodec}
import dev.profunktor.redis4cats.effect.Log
import dev.profunktor.redis4cats.streams.{RedisStream, Streaming}
import dev.profunktor.redis4cats.{Redis, RedisCommands}
import fs2.Stream
import io.lettuce.core.{ReadFrom as JReadFrom, RedisURI as JRedisURI}
import scodec.bits.ByteVector

import scala.jdk.CollectionConverters.given

sealed private trait ConnectionCreator[F[_]]:

  def createStreamingConnection
      : Stream[F, Streaming[[A] =>> Stream[F, A], String, ByteVector]]

  def createStringCommands: Resource[F, RedisCommands[F, String, String]]

object ConnectionCreator:

  def create[F[_]: Async: Log](cfg: RedisConfig): Resource[F, ConnectionCreator[F]] =
    val uri = redisUri(cfg)
    if cfg.sentinel then
      Resource.eval[F, ConnectionCreator[F]] {
        ApplicativeThrow[F]
          .catchNonFatal {
            uri.getSentinels.asScala.toList.map(RedisURI.fromUnderlying)
          }
          .map(new SentinelConnectionCreator(_))
      }
    else
      RedisClient[F]
        .fromUri(RedisURI.fromUnderlying(uri))
        .map(new SingleConnectionCreator(_))

  private def redisUri(cfg: RedisConfig): JRedisURI = {

    val uriBuilder = JRedisURI.builder
    cfg.maybeDB.map(_.value).foreach(uriBuilder.withDatabase)

    if cfg.sentinel then
      cfg.maybePassword.fold(
        uriBuilder.withSentinel(cfg.host.value, cfg.port.value)
      )(pass => uriBuilder.withSentinel(cfg.host.value, cfg.port.value, pass.value))
      cfg.maybeMasterSet.map(_.value).foreach(uriBuilder.withSentinelMasterId)
    else
      uriBuilder
        .withHost(cfg.host.value)
        .withPort(cfg.port.value)
      cfg.maybePassword.foreach(pass => uriBuilder.withPassword(pass.value.toCharArray))

    uriBuilder.build()
  }

private class SingleConnectionCreator[F[_]: Async: Log](client: RedisClient)
    extends ConnectionCreator[F]:

  override def createStreamingConnection
      : Stream[F, Streaming[[A] =>> Stream[F, A], String, ByteVector]] =
    RedisStream
      .mkStreamingConnection[F, String, ByteVector](client, StringBytesCodec.instance)

  override def createStringCommands: Resource[F, RedisCommands[F, String, String]] =
    Redis[F].fromClient(client, RedisCodec.Utf8)

private class SentinelConnectionCreator[F[_]: Async: Log](uris: List[RedisURI])
    extends ConnectionCreator[F]:

  private val maybeReadFrom: Option[JReadFrom] = ReadFrom.UpstreamPreferred.some

  override def createStreamingConnection
      : Stream[F, Streaming[[A] =>> Stream[F, A], String, ByteVector]] =
    RedisStream.mkMasterReplicaConnection[F, String, ByteVector](
      StringBytesCodec.instance,
      uris: _*
    )(maybeReadFrom)

  override def createStringCommands: Resource[F, RedisCommands[F, String, String]] =
    RedisMasterReplica[F]
      .make(RedisCodec.Utf8, uris: _*)(maybeReadFrom)
      .flatMap(Redis[F].masterReplica)
