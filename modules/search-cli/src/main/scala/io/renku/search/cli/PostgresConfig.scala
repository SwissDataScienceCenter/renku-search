package io.renku.search.cli

import cats.effect.IO
import cats.syntax.all.*
import com.comcast.ip4s.{Host, Port}
import ciris.*
import io.renku.search.config.ConfigDecoders

final case class PostgresConfig(
    host: Host,
    port: Port,
    user: String,
    password: Option[String],
    database: String
)

object PostgresConfig:
  def load: IO[PostgresConfig] = ConfigValues.config.load[IO]

  object ConfigValues extends ConfigDecoders {
    private val host = env("RS_POSTGRES_HOST").default("localhost").as[Host]
    private val port = env("RS_POSTGRES_PORT").default("5432").as[Port]
    private val user = env("RS_POSTGRES_USER")
    private val pass = env("RS_POSTGRES_PASS").option
    private val db = env("RS_POSTGRES_DATABASE")
    val config =
      (host,port,user,pass,db).mapN(PostgresConfig.apply)
  }
