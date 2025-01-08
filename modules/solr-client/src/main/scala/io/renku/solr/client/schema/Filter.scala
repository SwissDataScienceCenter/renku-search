package io.renku.solr.client.schema

import scala.compiletime.*
import scala.deriving.Mirror

// see https://solr.apache.org/guide/solr/latest/indexing-guide/filters.html

final case class Filter(name: String, settings: Option[Filter.Settings] = None)

object Filter:
  val asciiFolding: Filter = Filter("asciiFolding")
  val lowercase: Filter = Filter("lowercase")
  val stop: Filter = Filter("stop")
  val englishMinimalStem: Filter = Filter("englishMinimalStem")
  val classic: Filter = Filter("classic")
  val daitchMokotoffSoundex: Filter = Filter("daitchMokotoffSoundex")
  val doubleMetaphone: Filter = Filter("doubleMetaphone")
  val nGram: Filter = Filter("nGram")
  def edgeNGram(cfg: EdgeNGramSettings): Filter =
    val settings = Macros.settingsOf(cfg)
    Filter("edgeNGram", settings)

  /** Settings specific to a filter */
  opaque type Settings = Map[String, String]
  object Settings {
    def createFromMap(m: Map[String, String]): Option[Settings] =
      if (m.isEmpty) None else Some(m)
    extension (self: Settings)
      def asMap: Map[String, String] = self
      def get(key: String): Option[String] = self.get(key)
  }

  final case class EdgeNGramSettings(
      minGramSize: Int = 3,
      maxGramSize: Int = 6,
      preserveOriginal: Boolean = true
  )

  // SOLR encodes settings as strings. When doing schema requests, it
  // accepts both: JSON Number and JSON strings. However, when
  // querying the solr schema, it returns all filter settings as
  // strings, so it will be `"maxGramSize:"6"` instead of
  // `"maxGramSize:6`. So here every setting is encoded as a simple
  // `Map[String,String]`. Creating such settings should always happen
  // using a specific type, like `EdgeNGramSettings`.
  private object Macros {
    // This marco converts a case class into a Map[String,String] by
    // simply putting each member in it using the `toString` method.
    inline def settingsOf[A <: Product](value: A)(using
        m: Mirror.ProductOf[A]
    ): Option[Settings] =
      val values = value.asInstanceOf[Product].productIterator.toList
      val labels = constValueTuple[m.MirroredElemLabels]
      val kv = labels.toList.zip(values).map { case (k, v) => k.toString -> v.toString }
      Settings.createFromMap(kv.toMap)
  }
