package io.renku.search.solr

import io.renku.search.model.Id

enum SearchRole:
  case Admin(id: Id)
  case User(id: Id)
  case Anonymous

object SearchRole:
  def admin(id: Id): SearchRole = Admin(id)
  val anonymous: SearchRole = Anonymous
  def user(id: Id): SearchRole = User(id)
