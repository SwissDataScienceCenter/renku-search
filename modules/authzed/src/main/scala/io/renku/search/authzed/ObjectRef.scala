package io.renku.search.authzed

import com.authzed.api.v1.*

final case class ObjectRef(
    objectType: String,
    id: String
):

  private[authzed] lazy val toUnderlying: ObjectReference =
    ObjectReference.newBuilder().setObjectType(objectType).setObjectId(id).build()

object ObjectRef:

  def parse(str: String): Either[String, ObjectRef] =
    str.indexOf(':') match
      case n if n <= 0 => Left(s"Invalid object reference: $str")
      case n           => Right(ObjectRef(str.take(n), str.drop(n + 1)))

  def unsafeParse(str: String): ObjectRef =
    parse(str).fold(sys.error, identity)
