package io.renku.search.authzed

final case class LookupResourceRequest(
    resourceType: String,
    permission: String,
    subject: ObjectRef,
    limit: Option[Int] = None,
    cursor: Option[String] = None
)
