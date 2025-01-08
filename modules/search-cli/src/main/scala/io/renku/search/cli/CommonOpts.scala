package io.renku.search.cli

import cats.syntax.all.*

import com.monovore.decline.Argument
import com.monovore.decline.Opts
import io.renku.search.model.*

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

  given Argument[Description] =
    Argument.readString.map(Description(_))

  given Argument[Keyword] =
    Argument.readString.map(Keyword(_))

  given Argument[FirstName] =
    Argument.readString.map(FirstName(_))

  given Argument[LastName] =
    Argument.readString.map(LastName(_))

  given Argument[Email] =
    Argument.readString.map(Email(_))

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

  val projectDescription: Opts[Option[Description]] =
    Opts.option[Description]("description", "The project description").orNone

  val firstName: Opts[Option[FirstName]] =
    Opts.option[FirstName]("first-name", "The first name").orNone

  val lastName: Opts[Option[LastName]] =
    Opts.option[LastName]("last-name", "The last name").orNone

  val email: Opts[Option[Email]] =
    Opts.option[Email]("email", "The email address").orNone

object CommonOpts extends CommonOpts
