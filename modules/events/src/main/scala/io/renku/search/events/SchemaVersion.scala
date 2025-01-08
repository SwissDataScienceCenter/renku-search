package io.renku.search.events

import cats.data.NonEmptyList

enum SchemaVersion:
  case V1
  case V2

  lazy val name: String = productPrefix

object SchemaVersion:
  val all: NonEmptyList[SchemaVersion] =
    NonEmptyList.fromListUnsafe(SchemaVersion.values.toList)

  // the avro schema defines the version to be a string, not an enum
  // we try a few values that would make sense here
  private val candidateValues = all.toList.map { v =>
    v -> Set(v.name, v.name.toLowerCase(), v.name.drop(1))
  }.toMap

  def fromString(s: String): Either[String, SchemaVersion] =
    candidateValues
      .find(_._2.contains(s))
      .map(_._1)
      .toRight(s"Invalid schema version: $s")
