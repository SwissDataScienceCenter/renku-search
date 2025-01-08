package io.renku.search.solr.documents

import io.renku.solr.client.DocVersion
import io.renku.solr.client.ResponseBody
import munit.Assertions.assert

object EntityOps extends EntityOps
trait EntityOps:

  extension (entity: EntityDocument)

    def noneScore: EntityDocument = entity match {
      case e: Project => e.copy(score = None)
      case e: User    => e.copy(score = None)
      case e: Group   => e.copy(score = None)
    }

    def setCreatedBy(user: Option[ResponseBody[User]]): EntityDocument =
      entity match
        case e: Project => e.copy(creatorDetails = user)
        case e          => e

    def setNamespaceDetails(n: Option[ResponseBody[NestedUserOrGroup]]): EntityDocument =
      entity match
        case e: Project => e.copy(namespaceDetails = n)
        case e          => e

    def assertVersionNot(v: DocVersion): EntityDocument =
      assert(entity.version != v)
      entity
