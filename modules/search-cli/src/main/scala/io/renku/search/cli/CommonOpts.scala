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

package io.renku.search.cli

import cats.syntax.all.*
import com.monovore.decline.Opts
import io.renku.search.model.*
import io.renku.search.model.projects.{
  Description as ProjectDescription,
  Repository,
  Slug,
  Visibility
}
import com.monovore.decline.Argument

trait CommonOpts:

  given Argument[Name] =
    Argument.readString.map(Name(_))

  given Argument[Namespace] =
    Argument.readString.map(Namespace(_))

  given Argument[Id] =
    Argument.readString.map(Id(_))

  given Argument[MemberRole] =
    Argument.from("role") { str =>
      MemberRole.fromString(str).toValidatedNel
    }

  given Argument[Slug] =
    Argument.readString.map(Slug(_))

  given Argument[Visibility] =
    Argument.from("visibility") { str =>
      Visibility.fromString(str).toValidatedNel
    }

  given Argument[Repository] =
    Argument.readString.map(Repository(_))

  given Argument[ProjectDescription] =
    Argument.readString.map(ProjectDescription(_))

  given Argument[Keyword] =
    Argument.readString.map(Keyword(_))

  val nameOpt: Opts[Name] =
    Opts.option[Name]("name", "The name of the entity")

  val namespaceOpt: Opts[Namespace] =
    Opts.option[Namespace]("namespace", "A namespace string")

  val idOpt: Opts[Id] =
    Opts.option[Id]("id", "The entity id")

  val userIdOpt: Opts[Id] =
    Opts.option[Id]("user-id", "The user id")

  val groupIdOpt: Opts[Id] =
    Opts.option[Id]("group-id", "The group id")

  val projectIdOpt: Opts[Id] =
    Opts.option("project-id", "The project id")

  val roleOpt: Opts[MemberRole] =
    Opts.option[MemberRole]("role", "The role name")

  val projectSlug: Opts[Slug] =
    Opts.option[Slug]("slug", "A project slug")

  val projectVisibility: Opts[Visibility] =
    Opts
      .option[Visibility]("visibility", "Project visibility")
      .withDefault(Visibility.Public)

  val currentTime: Opts[Timestamp] =
    Opts(Timestamp(java.time.Instant.now()))

  val repositories: Opts[Seq[Repository]] =
    Opts
      .options[Repository]("repo", "project repositories")
      .map(_.toList)
      .withDefault(Nil)

  val keywords: Opts[Seq[Keyword]] =
    Opts
      .options[Keyword]("keyword", "list of keywords")
      .map(_.toList)
      .withDefault(Nil)

  val projectDescription: Opts[Option[ProjectDescription]] =
    Opts.option[ProjectDescription]("description", "The project description").orNone

object CommonOpts extends CommonOpts
