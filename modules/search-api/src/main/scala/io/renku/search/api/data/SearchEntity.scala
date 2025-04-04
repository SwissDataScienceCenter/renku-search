package io.renku.search.api.data

import io.bullet.borer.*
import io.bullet.borer.NullOptions.given
import io.bullet.borer.derivation.MapBasedCodecs
import io.renku.json.EncoderSupport
import io.renku.search.model.*

sealed trait SearchEntity:
  def id: Id
  def widen: SearchEntity = this

sealed trait UserOrGroup:
  def id: Id

object UserOrGroup:
  given AdtEncodingStrategy = AdtEncodingStrategy.flat(SearchEntity.discriminatorField)
  given Decoder[UserOrGroup] = MapBasedCodecs.deriveDecoder[UserOrGroup]
  given Encoder[UserOrGroup] = EncoderSupport.derive[UserOrGroup]

object SearchEntity:
  private[api] val discriminatorField = "type"
  given AdtEncodingStrategy = AdtEncodingStrategy.flat(discriminatorField)
  given Decoder[SearchEntity] = MapBasedCodecs.deriveDecoder[SearchEntity]
  given Encoder[SearchEntity] = EncoderSupport.derive[SearchEntity]

  final case class Project(
      id: Id,
      name: Name,
      slug: Slug,
      namespace: Option[UserOrGroup],
      repositories: Seq[Repository],
      visibility: Visibility,
      description: Option[Description] = None,
      createdBy: Option[User],
      creationDate: CreationDate,
      keywords: List[Keyword] = Nil,
      score: Option[Double] = None
  ) extends SearchEntity

  object Project:
    given Encoder[Project] =
      EncoderSupport.deriveWithDiscriminator[Project](discriminatorField)
    given Decoder[Project] = MapBasedCodecs.deriveDecoder
  end Project

  final case class User(
      id: Id,
      namespace: Option[Namespace] = None,
      firstName: Option[FirstName] = None,
      lastName: Option[LastName] = None,
      score: Option[Double] = None
  ) extends SearchEntity
      with UserOrGroup

  object User:
    given Encoder[User] = EncoderSupport.deriveWithDiscriminator(discriminatorField)
    given Decoder[User] = MapBasedCodecs.deriveDecoder
  end User

  final case class Group(
      id: Id,
      name: Name,
      namespace: Namespace,
      description: Option[Description] = None,
      score: Option[Double] = None
  ) extends SearchEntity
      with UserOrGroup
  object Group:
    given Encoder[Group] = EncoderSupport.deriveWithDiscriminator(discriminatorField)
    given Decoder[Group] = MapBasedCodecs.deriveDecoder
  end Group
