import sbt.*

//noinspection TypeAnnotation
object Dependencies {

  object V {
    val avro = "1.11.3"
    val avro4s = "5.0.9"
    val borer = "1.14.0"
    val catsCore = "2.10.0"
    val catsEffect = "3.5.4"
    val catsEffectMunit = "1.0.7"
    val catsParse = "1.0.0"
    val catsScalaCheck = "0.3.2"
    val ciris = "3.6.0"
    val decline = "2.4.1"
    val ducktape = "0.2.0"
    val fs2 = "3.10.2"
    val http4s = "0.23.27"
    val http4sPrometheusMetrics = "0.24.6"
    val redis4Cats = "1.7.0"
    val sbtMdoc = "2.5.2"
    val scala = "3.4.1"
    val scalacheckEffectMunit = "1.0.4"
    val scodec = "2.2.2"
    val scodecBits = "1.1.38"
    val scribe = "3.13.5"
    val sttpApiSpec = "0.9.0"
    val tapir = "1.10.7"
    val jwtScala = "10.0.1";
  }

  val jwtScala = Seq(
    "com.github.jwt-scala" %% "jwt-core" % V.jwtScala
  )

  val catsScalaCheck = Seq(
    "io.chrisdavenport" %% "cats-scalacheck" % V.catsScalaCheck
  )

  val catsParse = Seq(
    "org.typelevel" %% "cats-parse" % V.catsParse
  )

  val ciris = Seq(
    "is.cir" %% "ciris" % V.ciris
  )

  val borer = Seq(
    "io.bullet" %% "borer-core" % V.borer,
    "io.bullet" %% "borer-derivation" % V.borer,
    "io.bullet" %% "borer-compat-cats" % V.borer,
    "io.bullet" %% "borer-compat-scodec" % V.borer
  )

  val scodec = Seq(
    "org.scodec" %% "scodec-core" % V.scodec
  )

  val scodecBits = Seq(
    "org.scodec" %% "scodec-bits" % V.scodecBits
  )

  val avro4s = Seq(
    "com.sksamuel.avro4s" %% "avro4s-core" % V.avro4s
    // "com.sksamuel.avro4s" %% "avro4s-cats" % V.avro4s
  )

  val avro = Seq(
    "org.apache.avro" % "avro" % V.avro
  )

  val catsCore = Seq(
    "org.typelevel" %% "cats-core" % V.catsCore
  )

  val catsFree = Seq(
    "org.typelevel" %% "cats-free" % V.catsCore
  )

  val catsEffect = Seq(
    "org.typelevel" %% "cats-effect" % V.catsEffect
  )

  val catsEffectMunit = Seq(
    "org.typelevel" %% "munit-cats-effect-3" % V.catsEffectMunit
  )

  val decline = Seq(
    "com.monovore" %% "decline" % V.decline,
    "com.monovore" %% "decline-effect" % V.decline
  )

  val ducktape = Seq(
    "io.github.arainko" %% "ducktape" % V.ducktape
  )

  val fs2Core = Seq(
    "co.fs2" %% "fs2-core" % V.fs2
  )

  val http4sCore = Seq(
    "org.http4s" %% "http4s-core" % V.http4s
  )
  val http4sDsl = Seq(
    "org.http4s" %% "http4s-dsl" % V.http4s
  )
  val http4sClient = Seq(
    "org.http4s" %% "http4s-ember-client" % V.http4s
  )
  val http4sServer = Seq(
    "org.http4s" %% "http4s-ember-server" % V.http4s
  )
  val http4sPrometheusMetrics = Seq(
    "org.http4s" %% "http4s-server" % V.http4s,
    "org.http4s" %% "http4s-prometheus-metrics" % V.http4sPrometheusMetrics
  )

  val scribe = Seq(
    "com.outr" %% "scribe" % V.scribe,
    "com.outr" %% "scribe-slf4j2" % V.scribe,
    "com.outr" %% "scribe-cats" % V.scribe
  )

  val redis4Cats = Seq(
    "dev.profunktor" %% "redis4cats-effects" % V.redis4Cats
  )

  val redis4CatsStreams = Seq(
    "dev.profunktor" %% "redis4cats-streams" % V.redis4Cats
  )

  val scalacheckEffectMunit = Seq(
    "org.typelevel" %% "scalacheck-effect-munit" % V.scalacheckEffectMunit
  )

  val tapirCore = Seq(
    "com.softwaremill.sttp.tapir" %% "tapir-core" % V.tapir
  )
  val tapirHttp4sServer = Seq(
    "com.softwaremill.sttp.tapir" %% "tapir-http4s-server" % V.tapir
  )
  val tapirOpenAPI = Seq(
    "com.softwaremill.sttp.tapir" %% "tapir-openapi-docs" % V.tapir,
    "com.softwaremill.sttp.apispec" %% "openapi-circe-yaml" % V.sttpApiSpec
  )
}
