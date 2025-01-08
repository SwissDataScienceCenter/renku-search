package io.renku.search.common

import io.bullet.borer.Decoder
import io.bullet.borer.Encoder
import io.bullet.borer.derivation.MapBasedCodecs

final case class CurrentVersion(
    name: String,
    version: String,
    headCommit: String,
    describedVersion: String
)

object CurrentVersion:

  given Encoder[CurrentVersion] = MapBasedCodecs.deriveEncoder
  given Decoder[CurrentVersion] = MapBasedCodecs.deriveDecoder

  lazy val get: CurrentVersion = CurrentVersion(
    name = "renku-search",
    version = io.renku.search.BuildInfo.version,
    headCommit = io.renku.search.BuildInfo.gitHeadCommit.getOrElse(""),
    describedVersion = io.renku.search.BuildInfo.gitDescribedVersion.getOrElse("")
  )
