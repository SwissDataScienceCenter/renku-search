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

import cats.effect.{Async, Resource}
import cats.syntax.all.*
import dev.profunktor.redis4cats.connection.{RedisClient, RedisMasterReplica, RedisURI}
import dev.profunktor.redis4cats.data.{ReadFrom, RedisCodec}
import dev.profunktor.redis4cats.effect.Log
import dev.profunktor.redis4cats.streams.{RedisStream, Streaming}
import dev.profunktor.redis4cats.{Redis, RedisCommands}
import fs2.Stream
import io.lettuce.core.{
  RedisCredentials,
  RedisURI as JRedisURI,
  StaticCredentialsProvider
}
import scodec.bits.ByteVector

sealed private trait ConnectionCreator[F[_]]:

  def createStreamingConnection
      : Stream[F, Streaming[[A] =>> Stream[F, A], String, ByteVector]]

  def createStringCommands: Resource[F, RedisCommands[F, String, String]]

object ConnectionCreator:

  def create[F[_]: Async: Log](cfg: RedisConfig): Resource[F, ConnectionCreator[F]] =
    val uri = createRedisUri(cfg)
    cfg.maybeMasterSet match {
      case None =>
        RedisClient[F]
          .fromUri(RedisURI.fromUnderlying(uri))
          .map(new SingleConnectionCreator(_))
      case Some(masterSet) =>
        uri.setSentinelMasterId(masterSet.value)
        Resource
          .pure[F, RedisURI](RedisURI.fromUnderlying(uri))
          .map(new MasterReplicaConnectionCreator(_))
    }

  private def createRedisUri(cfg: RedisConfig): JRedisURI = {
    val uri = JRedisURI.create(cfg.host.value, cfg.port.value)
    cfg.maybeDB.foreach(db => uri.setDatabase(db.value))
    cfg.maybePassword.foreach(pass =>
      uri.setCredentialsProvider(
        new StaticCredentialsProvider(RedisCredentials.just(null, pass.value))
      )
    )
    uri
  }

private class SingleConnectionCreator[F[_]: Async: Log](client: RedisClient)
    extends ConnectionCreator[F]:

  override def createStreamingConnection
      : Stream[F, Streaming[[A] =>> Stream[F, A], String, ByteVector]] =
    RedisStream
      .mkStreamingConnection[F, String, ByteVector](client, StringBytesCodec.instance)

  override def createStringCommands: Resource[F, RedisCommands[F, String, String]] =
    Redis[F].fromClient(client, RedisCodec.Utf8)

private class MasterReplicaConnectionCreator[F[_]: Async: Log](uri: RedisURI)
    extends ConnectionCreator[F]:

  override def createStreamingConnection
      : Stream[F, Streaming[[A] =>> Stream[F, A], String, ByteVector]] =
    RedisStream
      .mkMasterReplicaConnection[F, String, ByteVector](StringBytesCodec.instance, uri)(
        None
      )

  override def createStringCommands: Resource[F, RedisCommands[F, String, String]] =
    RedisMasterReplica[F]
      .make(RedisCodec.Utf8, uri)(ReadFrom.UpstreamPreferred.some)
      .flatMap(Redis[F].masterReplica)
