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

package io.renku.search.provision.events

import cats.Functor
import cats.syntax.all.*
import io.renku.events.{v1, v2}
import io.renku.search.provision.handler.IdExtractor
import io.renku.search.provision.events.syntax.*
import io.renku.search.provision.QueueMessageDecoder
import io.renku.search.provision.handler.DocumentMerger
import cats.Show

sealed trait ProjectCreated:
  def fold[A](fv1: v1.ProjectCreated => A, fv2: v2.ProjectCreated => A): A

object ProjectCreated:

  final case class V1(event: v1.ProjectCreated) extends ProjectCreated:
    def fold[A](fv1: v1.ProjectCreated => A, fv2: v2.ProjectCreated => A): A = fv1(event)

  final case class V2(event: v2.ProjectCreated) extends ProjectCreated:
    def fold[A](fv1: v1.ProjectCreated => A, fv2: v2.ProjectCreated => A): A = fv2(event)

  given IdExtractor[ProjectCreated] =
    IdExtractor.create(_.fold(_.id, _.id).toId)

  given [F[_]: Functor](using
      v1d: QueueMessageDecoder[F, v1.ProjectCreated],
      v2d: QueueMessageDecoder[F, v2.ProjectCreated]
  ): QueueMessageDecoder[F, ProjectCreated] =
    QueueMessageDecoder.instance { qmsg =>
      qmsg.header.schemaVersion.toLowerCase match
        case "v1" => v1d.decodeMessage(qmsg).map(_.map(V1(_)))
        case "v2" => v2d.decodeMessage(qmsg).map(_.map(V2(_)))
        case _    => sys.error("peng")
    }

  given (using
      v1m: DocumentMerger[v1.ProjectCreated],
      v2m: DocumentMerger[v2.ProjectCreated]
  ): DocumentMerger[ProjectCreated] =
    DocumentMerger.instance[ProjectCreated] {
      case V1(event) => v1m.create(event)
      case V2(event) => v2m.create(event)
    } {
      case (V1(event), existing) => v1m.merge(event, existing)
      case (V2(event), existing) => v2m.merge(event, existing)
    }

  given Show[ProjectCreated] =
    import io.renku.search.provision.handler.ShowInstances.given
    Show.show(_.fold(_.show, _.show))
