package io.renku.search.solr.documents

import cats.syntax.all.*

import io.bullet.borer.Decoder
import io.bullet.borer.NullOptions.given
import io.bullet.borer.derivation.{MapBasedCodecs, key}
import io.renku.search.model.EntityType
import io.renku.search.model.Id
import io.renku.search.solr.query.SolrToken
import io.renku.solr.client.{QueryData, QueryString}

final case class CompoundId(
    id: Id,
    @key("_kind") kind: DocumentKind,
    @key("_type") entityType: Option[EntityType] = None
):
  private[solr] def toQuery: SolrToken =
    List(
      SolrToken.idIs(id),
      SolrToken.kindIs(kind),
      entityType.map(SolrToken.entityTypeIs).getOrElse(SolrToken.empty)
    ).foldAnd

  private[solr] def toQueryData: QueryData =
    QueryData(QueryString(toQuery.value, 1, 0))

object CompoundId:
  given Decoder[CompoundId] = MapBasedCodecs.deriveDecoder

  def partial(id: Id, entityType: Option[EntityType] = None): CompoundId =
    CompoundId(id, DocumentKind.PartialEntity, entityType)

  def entity(id: Id, entityType: Option[EntityType] = None): CompoundId =
    CompoundId(id, DocumentKind.FullEntity, entityType)

  def projectEntity(id: Id): CompoundId = entity(id, EntityType.Project.some)
  def userEntity(id: Id): CompoundId = entity(id, EntityType.User.some)
  def groupEntity(id: Id): CompoundId = entity(id, EntityType.Group.some)

  def projectPartial(id: Id): CompoundId = partial(id, EntityType.Project.some)
  def groupPartial(id: Id): CompoundId = partial(id, EntityType.Group.some)
