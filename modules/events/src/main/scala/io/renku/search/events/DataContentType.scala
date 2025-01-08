package io.renku.search.events

enum DataContentType(val mimeType: String):
  lazy val name: String = productPrefix
  case Binary extends DataContentType("application/avro+binary")
  case Json extends DataContentType("application/avro+json")

object DataContentType:
  def fromMimeType(mimeType: String): Either[String, DataContentType] =
    DataContentType.values.toList
      .find(_.mimeType == mimeType)
      .toRight(s"'$mimeType' not a valid 'DataContentType' value")
