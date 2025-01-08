package io.renku.redis.client

import java.nio.ByteBuffer

import dev.profunktor.redis4cats.data.RedisCodec
import io.lettuce.core.codec.{ByteArrayCodec, RedisCodec as JRedisCodec, StringCodec}
import scodec.bits.ByteVector

object StringBytesCodec:

  val instance: RedisCodec[String, ByteVector] = RedisCodec {
    new JRedisCodec[String, ByteVector] {

      override def decodeKey(bytes: ByteBuffer): String =
        StringCodec.UTF8.decodeKey(bytes)

      override def decodeValue(bytes: ByteBuffer): ByteVector =
        ByteVector.view(ByteArrayCodec.INSTANCE.decodeValue(bytes))

      override def encodeKey(key: String): ByteBuffer =
        StringCodec.UTF8.encodeKey(key)

      override def encodeValue(value: ByteVector): ByteBuffer =
        ByteArrayCodec.INSTANCE.encodeValue(value.toArray)
    }
  }
