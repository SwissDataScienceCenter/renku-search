package io.renku.search.authzed

final case class CheckPermissionRequest(
    objectRef: ObjectRef,
    subjectRef: ObjectRef,
    permission: String
)
