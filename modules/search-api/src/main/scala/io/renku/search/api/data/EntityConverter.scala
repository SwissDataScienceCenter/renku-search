package io.renku.search.api.data

import io.renku.search.solr.documents.{
  Group as GroupDocument,
  Project as ProjectDocument,
  User as UserDocument,
  *
}

trait EntityConverter:
  def project(p: ProjectDocument): SearchEntity.Project =
    SearchEntity.Project(
      id = p.id,
      name = p.name,
      slug = p.slug,
      namespace = p.namespaceDetails.flatMap(_.docs.headOption).flatMap {
        case u: UserDocument  => Some(user(u))
        case g: GroupDocument => Some(group(g))
        case v =>
          scribe.error(
            s"Error converting project namespace due to incorrect data. A user or group was expected as the project namespace of ${p.id}/${p.slug}, but found: $v"
          )
          None
      },
      repositories = p.repositories,
      visibility = p.visibility,
      description = p.description,
      createdBy = p.creatorDetails
        .flatMap(_.docs.headOption)
        .map(user)
        .orElse(Some(SearchEntity.User(p.createdBy))),
      creationDate = p.creationDate,
      keywords = p.keywords,
      score = p.score
    )

  def user(u: UserDocument): SearchEntity.User =
    SearchEntity.User(
      id = u.id,
      namespace = u.namespace,
      firstName = u.firstName,
      lastName = u.lastName,
      score = u.score
    )

  def group(g: GroupDocument): SearchEntity.Group =
    SearchEntity.Group(
      id = g.id,
      name = g.name,
      namespace = g.namespace,
      description = g.description,
      score = g.score
    )

  def entity(e: EntityDocument): SearchEntity = e match
    case p: ProjectDocument => project(p)
    case u: UserDocument    => user(u)
    case g: GroupDocument   => group(g)
end EntityConverter

object EntityConverter extends EntityConverter:
  def apply(e: EntityDocument): SearchEntity = entity(e)
