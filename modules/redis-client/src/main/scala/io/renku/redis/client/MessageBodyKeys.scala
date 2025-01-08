package io.renku.redis.client

enum MessageBodyKeys:
  case Headers
  case Payload

  lazy val name: String = productPrefix.toLowerCase()
