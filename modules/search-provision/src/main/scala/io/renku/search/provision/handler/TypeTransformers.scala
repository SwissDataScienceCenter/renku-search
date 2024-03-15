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

import io.github.arainko.ducktape.Transformer
import io.renku.events.v1
import io.renku.search.model.projects

object TypeTransformers extends TypeTransformers

trait TypeTransformers:

  given Transformer[v1.Visibility, projects.Visibility] =
    (from: v1.Visibility) => projects.Visibility.unsafeFromString(from.name())

  given memberRoleTransformer: Transformer[v1.ProjectMemberRole, projects.MemberRole] =
    (from: v1.ProjectMemberRole) => projects.MemberRole.unsafeFromString(from.name())