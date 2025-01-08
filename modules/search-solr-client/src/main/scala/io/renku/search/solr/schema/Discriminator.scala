package io.renku.search.solr.schema

opaque type Discriminator = String
object Discriminator:
  def apply(name: String): Discriminator = name

  extension (self: Discriminator) def name: String = self

  val project: Discriminator = "project"
