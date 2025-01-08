package io.renku.solr.client

import io.bullet.borer.Decoder
import io.bullet.borer.Encoder

/** A solr version as described in [optimistic
  * locking](https://solr.apache.org/guide/solr/latest/indexing-guide/partial-document-updates.html#optimistic-concurrency)
  */
enum DocVersion:
  case Exact(version: Long)
  case Exists
  case NotExists
  case Off

  lazy val asLong: Long = this match
    case Exact(n)  => n
    case Exists    => 1
    case NotExists => -1
    case Off       => 0

object DocVersion:
  given Decoder[DocVersion] =
    Decoder.forLong.map(fromLong)

  given Encoder[DocVersion] =
    Encoder.forLong.contramap(_.asLong)

  def fromLong(version: Long): DocVersion =
    version match
      case _ if version > 1  => Exact(version)
      case _ if version == 1 => Exists
      case _ if version < 0  => NotExists
      case _                 => Off
