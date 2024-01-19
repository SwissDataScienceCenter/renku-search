import sbt.*

//noinspection TypeAnnotation
object Dependencies {

  object V {
    val avro = "1.11.1"
    val avro4s = "5.0.9"
    val catsCore = "2.10.0"
    val catsEffect = "3.5.3"
    val catsEffectMunit = "1.0.7"
    val fs2 = "3.9.3"
    val redis4Cats = "1.5.2"
    val scalacheckEffectMunit = "1.0.4"
    val scodec = "2.2.2"
    val scodecBits = "1.1.38"
    val scribe = "3.13.0"
  }

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

  val fs2Core = Seq(
    "co.fs2" %% "fs2-core" % V.fs2
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
}
