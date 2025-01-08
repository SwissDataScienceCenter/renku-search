package io.renku.search.query

import cats.data.NonEmptyList

import io.renku.search.model.*

enum FieldTerm(val field: Field, val cmp: Comparison):
  case TypeIs(values: NonEmptyList[EntityType])
      extends FieldTerm(Field.Type, Comparison.Is)
  case IdIs(values: NonEmptyList[String]) extends FieldTerm(Field.Id, Comparison.Is)
  case NameIs(values: NonEmptyList[String]) extends FieldTerm(Field.Name, Comparison.Is)
  case SlugIs(values: NonEmptyList[String]) extends FieldTerm(Field.Slug, Comparison.Is)
  case VisibilityIs(values: NonEmptyList[Visibility])
      extends FieldTerm(Field.Visibility, Comparison.Is)
  case Created(override val cmp: Comparison, values: NonEmptyList[DateTimeRef])
      extends FieldTerm(Field.Created, cmp)
  case CreatedByIs(values: NonEmptyList[String])
      extends FieldTerm(Field.CreatedBy, Comparison.Is)
  case RoleIs(values: NonEmptyList[MemberRole])
      extends FieldTerm(Field.Role, Comparison.Is)
  case KeywordIs(values: NonEmptyList[Keyword])
      extends FieldTerm(Field.Keyword, Comparison.Is)
  case NamespaceIs(values: NonEmptyList[Namespace])
      extends FieldTerm(Field.Namespace, Comparison.Is)

  private[query] def asString =
    val value = this match
      case TypeIs(values) =>
        val ts = values.toList.distinct.map(_.name)
        ts.mkString(",")
      case IdIs(values)   => FieldTerm.nelToString(values)
      case NameIs(values) => FieldTerm.nelToString(values)
      case SlugIs(values) => FieldTerm.nelToString(values)
      case VisibilityIs(values) =>
        val vis = values.toList.distinct.map(_.name)
        vis.mkString(",")
      case Created(_, values)  => FieldTerm.nelToString(values.map(_.asString))
      case CreatedByIs(values) => FieldTerm.nelToString(values)
      case RoleIs(values)      => FieldTerm.nelToString(values.map(_.name))
      case KeywordIs(values)   => FieldTerm.nelToString(values.map(_.value))
      case NamespaceIs(values) => FieldTerm.nelToString(values.map(_.value))

    s"${field.name}${cmp.asString}${value}"

object FieldTerm:
  private def quote(s: String): String =
    if (s.exists(c => c.isWhitespace || c == ',' || c == '"'))
      s"\"${s.replace("\"", "\\\"")}\""
    else s

  private def nelToString(nel: NonEmptyList[String]): String =
    nel.map(quote).toList.mkString(",")
