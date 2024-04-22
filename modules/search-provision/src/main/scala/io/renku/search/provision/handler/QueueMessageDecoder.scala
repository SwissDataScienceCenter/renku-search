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

package io.renku.search.provision.handler

import cats.MonadThrow
import cats.syntax.all.*
import io.renku.avro.codec.all.given
import io.renku.avro.codec.{AvroDecoder, AvroReader}
import io.renku.events.{v1, v2}
import io.renku.queue.client.{DataContentType, QueueMessage}
import io.renku.search.events.*
import io.renku.search.events.syntax.*
import org.apache.avro.Schema

trait QueueMessageDecoder[F[_], A]:
  def decodeMessage(message: QueueMessage): F[Seq[A]]

object QueueMessageDecoder:

  def instance[F[_], A](f: QueueMessage => F[Seq[A]]): QueueMessageDecoder[F, A] =
    new QueueMessageDecoder[F, A] {
      def decodeMessage(message: QueueMessage): F[Seq[A]] = f(message)
    }

  private def forSchemaVersion[F[_]: MonadThrow, A](
      f: (QueueMessage, SchemaVersion) => F[Seq[A]]
  ): QueueMessageDecoder[F, A] =
    instance { qmsg =>
      MonadThrow[F]
        .fromEither(
          SchemaVersion.fromString(qmsg.header.schemaVersion).leftMap(new Exception(_))
        )
        .flatMap(f(qmsg, _))
    }

  def from[F[_]: MonadThrow, A](schema: Schema)(using
      AvroDecoder[A]
  ): QueueMessageDecoder[F, A] = {
    val avro = AvroReader(schema)
    new QueueMessageDecoder[F, A]:
      def decodeMessage(message: QueueMessage): F[Seq[A]] =
        findContentType.andThenF(decodePayload(message))(message)

      private def findContentType(message: QueueMessage): F[DataContentType] =
        MonadThrow[F].fromEither(DataContentType.from(message.header.dataContentType))

      private def decodePayload(message: QueueMessage): DataContentType => F[Seq[A]] = {
        case DataContentType.Binary => catchNonFatal(avro.read[A](message.payload))
        case DataContentType.Json   => catchNonFatal(avro.readJson[A](message.payload))
      }

      private def catchNonFatal(f: => Seq[A]): F[Seq[A]] =
        MonadThrow[F].catchNonFatal(f)
  }

  def apply[F[_], A](using d: QueueMessageDecoder[F, A]): QueueMessageDecoder[F, A] = d

  given [F[_]: MonadThrow]: QueueMessageDecoder[F, v1.UserAdded] =
    from(v1.UserAdded.SCHEMA$)
  given [F[_]: MonadThrow]: QueueMessageDecoder[F, v1.UserUpdated] =
    from(v1.UserUpdated.SCHEMA$)
  given [F[_]: MonadThrow]: QueueMessageDecoder[F, v1.UserRemoved] =
    from(v1.UserRemoved.SCHEMA$)

  given [F[_]: MonadThrow]: QueueMessageDecoder[F, GroupAdded] =
    val v2d = from[F, v2.GroupAdded](v2.GroupAdded.SCHEMA$)
    QueueMessageDecoder.forSchemaVersion {
      case (_, SchemaVersion.V1) =>
        new Exception(s"GroupAdded does not exists for ${SchemaVersion.V1}")
          .raiseError[F, Seq[GroupAdded]]
      case (qmsg, SchemaVersion.V2) =>
        v2d.decodeMessage(qmsg).map(_.map(GroupAdded.V2.apply))
    }

  given [F[_]: MonadThrow]: QueueMessageDecoder[F, v2.GroupRemoved] =
    from(v2.GroupRemoved.SCHEMA$)

  given [F[_]: MonadThrow]: QueueMessageDecoder[F, v1.ProjectAuthorizationAdded] =
    from(v1.ProjectAuthorizationAdded.SCHEMA$)
  given [F[_]: MonadThrow]: QueueMessageDecoder[F, v1.ProjectAuthorizationUpdated] =
    from(v1.ProjectAuthorizationUpdated.SCHEMA$)
  given [F[_]: MonadThrow]: QueueMessageDecoder[F, v1.ProjectAuthorizationRemoved] =
    from(v1.ProjectAuthorizationRemoved.SCHEMA$)

  given [F[_]: MonadThrow]: QueueMessageDecoder[F, ProjectCreated] =
    val v1d = from[F, v1.ProjectCreated](v1.ProjectCreated.SCHEMA$)
    val v2d = from[F, v2.ProjectCreated](v2.ProjectCreated.SCHEMA$)
    QueueMessageDecoder.forSchemaVersion {
      case (qmsg, SchemaVersion.V1) =>
        v1d.decodeMessage(qmsg).map(_.map(ProjectCreated.V1.apply))
      case (qmsg, SchemaVersion.V2) =>
        v2d.decodeMessage(qmsg).map(_.map(ProjectCreated.V2.apply))
    }

  given [F[_]: MonadThrow]: QueueMessageDecoder[F, ProjectUpdated] =
    val v1d = from[F, v1.ProjectUpdated](v1.ProjectUpdated.SCHEMA$)
    val v2d = from[F, v2.ProjectUpdated](v2.ProjectUpdated.SCHEMA$)
    QueueMessageDecoder.forSchemaVersion {
      case (qmsg, SchemaVersion.V1) =>
        v1d.decodeMessage(qmsg).map(_.map(ProjectUpdated.V1.apply))
      case (qmsg, SchemaVersion.V2) =>
        v2d.decodeMessage(qmsg).map(_.map(ProjectUpdated.V2.apply))
    }

  given [F[_]: MonadThrow]: QueueMessageDecoder[F, ProjectRemoved] =
    val v1d = from[F, v1.ProjectRemoved](v1.ProjectRemoved.SCHEMA$)
    val v2d = from[F, v2.ProjectRemoved](v2.ProjectRemoved.SCHEMA$)
    QueueMessageDecoder.forSchemaVersion {
      case (qmsg, SchemaVersion.V1) =>
        v1d.decodeMessage(qmsg).map(_.map(e => ProjectRemoved(e.id.toId)))
      case (qmsg, SchemaVersion.V2) =>
        v2d.decodeMessage(qmsg).map(_.map(e => ProjectRemoved(e.id.toId)))
    }
