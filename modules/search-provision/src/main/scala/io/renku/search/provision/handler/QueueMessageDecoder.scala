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

package io.renku.search.provision

import cats.MonadThrow
import cats.syntax.all.*
import io.renku.avro.codec.{AvroDecoder, AvroReader}
import io.renku.avro.codec.all.given
import io.renku.queue.client.{DataContentType, QueueMessage}
import org.apache.avro.Schema
import io.renku.events.{v1, v2}
import io.renku.search.events.{ProjectCreated, ProjectUpdated, SchemaVersion}

trait QueueMessageDecoder[F[_], A]:
  def decodeMessage(message: QueueMessage): F[Seq[A]]

object QueueMessageDecoder:
  def instance[F[_], A](f: QueueMessage => F[Seq[A]]): QueueMessageDecoder[F, A] =
    new QueueMessageDecoder[F, A] {
      def decodeMessage(message: QueueMessage): F[Seq[A]] = f(message)
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

  given v1ProjectCreated[F[_]: MonadThrow]: QueueMessageDecoder[F, v1.ProjectCreated] =
    from(v1.ProjectCreated.SCHEMA$)
  given v2ProjectCreated[F[_]: MonadThrow]: QueueMessageDecoder[F, v2.ProjectCreated] =
    from(v2.ProjectCreated.SCHEMA$)

  given v1ProjectUpdated[F[_]: MonadThrow]: QueueMessageDecoder[F, v1.ProjectUpdated] =
    from(v1.ProjectUpdated.SCHEMA$)
  given v2ProjectUpdated[F[_]: MonadThrow]: QueueMessageDecoder[F, v2.ProjectUpdated] =
    from(v2.ProjectUpdated.SCHEMA$)

  given [F[_]: MonadThrow]: QueueMessageDecoder[F, v1.ProjectRemoved] =
    from(v1.ProjectRemoved.SCHEMA$)

  given [F[_]: MonadThrow]: QueueMessageDecoder[F, v1.UserAdded] =
    from(v1.UserAdded.SCHEMA$)
  given [F[_]: MonadThrow]: QueueMessageDecoder[F, v1.UserUpdated] =
    from(v1.UserUpdated.SCHEMA$)
  given [F[_]: MonadThrow]: QueueMessageDecoder[F, v1.UserRemoved] =
    from(v1.UserRemoved.SCHEMA$)

  given [F[_]: MonadThrow]: QueueMessageDecoder[F, v2.GroupAdded] =
    from(v2.GroupAdded.SCHEMA$)
  given [F[_]: MonadThrow]: QueueMessageDecoder[F, v2.GroupRemoved] =
    from(v2.GroupRemoved.SCHEMA$)

  given [F[_]: MonadThrow]: QueueMessageDecoder[F, v1.ProjectAuthorizationAdded] =
    from(v1.ProjectAuthorizationAdded.SCHEMA$)
  given [F[_]: MonadThrow]: QueueMessageDecoder[F, v1.ProjectAuthorizationUpdated] =
    from(v1.ProjectAuthorizationUpdated.SCHEMA$)
  given [F[_]: MonadThrow]: QueueMessageDecoder[F, v1.ProjectAuthorizationRemoved] =
    from(v1.ProjectAuthorizationRemoved.SCHEMA$)

  given [F[_]: MonadThrow](using
      v1d: QueueMessageDecoder[F, v1.ProjectCreated],
      v2d: QueueMessageDecoder[F, v2.ProjectCreated]
  ): QueueMessageDecoder[F, ProjectCreated] =
    QueueMessageDecoder.instance { qmsg =>
      MonadThrow[F]
        .fromEither(
          SchemaVersion
            .fromString(qmsg.header.schemaVersion)
            .leftMap(err => new Exception(err))
        )
        .flatMap {
          case SchemaVersion.V1 =>
            v1d.decodeMessage(qmsg).map(_.map(ProjectCreated.V1(_)))
          case SchemaVersion.V2 =>
            v2d.decodeMessage(qmsg).map(_.map(ProjectCreated.V2(_)))
        }
    }

  given [F[_]: MonadThrow](using
      v1d: QueueMessageDecoder[F, v1.ProjectUpdated],
      v2d: QueueMessageDecoder[F, v2.ProjectUpdated]
  ): QueueMessageDecoder[F, ProjectUpdated] =
    QueueMessageDecoder.instance { qmsg =>
      MonadThrow[F]
        .fromEither(
          SchemaVersion
            .fromString(qmsg.header.schemaVersion)
            .leftMap(err => new Exception(err))
        )
        .flatMap {
          case SchemaVersion.V1 =>
            v1d.decodeMessage(qmsg).map(_.map(ProjectUpdated.V1(_)))
          case SchemaVersion.V2 =>
            v2d.decodeMessage(qmsg).map(_.map(ProjectUpdated.V2(_)))
        }
    }
