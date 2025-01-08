package io.renku.redis.client

import org.scalacheck.Gen
import org.scalacheck.Gen.{alphaLowerChar, alphaNumChar}

object RedisClientGenerators:

  private val stringGen: Gen[String] =
    Gen
      .chooseNum(3, 10)
      .flatMap(Gen.stringOfN(_, alphaLowerChar))

  val queueNameGen: Gen[QueueName] =
    stringGen.map(QueueName(_))

  val clientIdGen: Gen[ClientId] =
    Gen
      .chooseNum(3, 10)
      .flatMap(Gen.stringOfN(_, alphaNumChar).map(ClientId(_)))

  val messageIdGen: Gen[MessageId] =
    for
      part1 <- Gen.chooseNum(3, 10)
      part2 <- Gen.chooseNum(3, 10)
    yield s"$part1.$part2"
