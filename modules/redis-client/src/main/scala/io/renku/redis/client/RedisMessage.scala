package io.renku.redis.client

import scodec.bits.ByteVector

final case class RedisMessage(id: String, header: ByteVector, payload: ByteVector)
