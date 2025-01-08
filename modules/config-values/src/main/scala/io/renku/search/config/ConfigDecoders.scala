package io.renku.search.config

import cats.Show
import cats.syntax.all.*
import ciris.{ConfigDecoder, ConfigError}
import com.comcast.ip4s.{Ipv4Address, Port}
import io.renku.redis.client.*
import org.http4s.Uri

import scala.concurrent.duration.{Duration, FiniteDuration}
import io.renku.search.common.UrlPattern
import io.renku.search.sentry.SentryDsn
import io.renku.search.sentry.SentryEnv
import io.renku.solr.client.SolrConfig

trait ConfigDecoders:
  extension [A, B](self: ConfigDecoder[A, B])
    def emap[C](typeName: String)(f: B => Either[String, C])(using Show[B]) =
      self.mapEither((key, b) =>
        f(b).left.map(err => ConfigError.decode(typeName, key, b))
      )

  given ConfigDecoder[String, Uri] =
    ConfigDecoder[String].mapEither { (_, s) =>
      Uri.fromString(s).leftMap(err => ConfigError(err.getMessage))
    }

  given ConfigDecoder[String, FiniteDuration] =
    ConfigDecoder[String].mapOption("duration") { s =>
      Duration.unapply(s).map(Duration.apply.tupled).filter(_.isFinite)
    }

  given ConfigDecoder[String, SolrConfig.SolrPassword] =
    ConfigDecoder[String].map(SolrConfig.SolrPassword.apply)

  given ConfigDecoder[String, RedisHost] =
    ConfigDecoder[String].map(RedisHost.apply)
  given ConfigDecoder[String, RedisPort] =
    ConfigDecoder[String, Int].map(RedisPort.apply)
  given ConfigDecoder[String, RedisDB] =
    ConfigDecoder[String, Int].map(RedisDB.apply)
  given ConfigDecoder[String, RedisPassword] =
    ConfigDecoder[String].map(RedisPassword.apply)
  given ConfigDecoder[String, RedisMasterSet] =
    ConfigDecoder[String].map(RedisMasterSet.apply)

  given ConfigDecoder[String, QueueName] =
    ConfigDecoder[String].map(s => QueueName(s))
  given ConfigDecoder[String, ClientId] =
    ConfigDecoder[String].map(s => ClientId(s))

  given ConfigDecoder[String, Ipv4Address] =
    ConfigDecoder[String]
      .mapOption(Ipv4Address.getClass.getSimpleName)(Ipv4Address.fromString)
  given ConfigDecoder[String, Port] =
    ConfigDecoder[String]
      .mapOption(Port.getClass.getSimpleName)(Port.fromString)

  given ConfigDecoder[String, List[UrlPattern]] =
    ConfigDecoder[String].emap("UrlPattern") { str =>
      str.split(',').toList.traverse(UrlPattern.fromString)
    }

  given ConfigDecoder[String, SentryDsn] =
    ConfigDecoder[String].emap("SentryDsn")(SentryDsn.fromString)

  given ConfigDecoder[String, SentryEnv] =
    ConfigDecoder[String].emap("SentryEnv")(SentryEnv.fromString)
