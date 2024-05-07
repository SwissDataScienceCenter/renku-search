/*
 * Copyright 2024 Swiss Data Science Center (SDSC)
 * A partnership between École Polytechnique Fédérale de Lausanne (EPFL) and
 * Eidgenössische Technische Hochschule Zürich (ETHZ).
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.renku.search.cli.perftests

import scala.concurrent.duration.*

import cats.syntax.all.*

import com.monovore.decline.Opts
import io.renku.redis.client.*
import org.http4s.Uri

final case class PerfTestsConfig(
    itemsToGenerate: Int,
    providers: List[Provider],
    dryRun: DryRun
)

object PerfTestsConfig:

  private val itemsToGenerate: Opts[Int] =
    Opts
      .option[Int]("items-to-generate", "Number of items to generate. Default: 20")
      .withDefault(20)
      .validate("`items-to-generate` must be greater than 0")(_ > 0)

  val configOpts: Opts[PerfTestsConfig] =
    (itemsToGenerate, Provider.configOpts, DryRun.configOpts)
      .mapN(PerfTestsConfig.apply)

sealed private trait DryRun:
  val widen: DryRun = this

private object DryRun:
  case object Yes extends DryRun
  final case class No(redisConfig: RedisConfig) extends DryRun

  private val dryRun =
    Opts
      .flag("dry-run", "To run without enqueueing to Redis")
      .map(_ => Yes)

  val configOpts: Opts[DryRun] =
    dryRun.orElse(RedisConfigOpts.configOpts.map(No.apply))

private object RedisConfigOpts:

  private val host: Opts[RedisHost] =
    Opts.option[String]("redis-host", "Redis host").map(RedisHost.apply)
  private val port: Opts[RedisPort] =
    Opts.option[Int]("redis-port", "Redis port").map(RedisPort.apply)
  private val sentinel: Opts[Boolean] =
    Opts.flag("sentinel", "if Redis in Sentinel setup").orFalse
  private val db: Opts[RedisDB] =
    Opts
      .option[Int]("redis-db", "Redis DB")
      .withDefault(3)
      .map(RedisDB.apply)
  private val password: Opts[Option[RedisPassword]] =
    Opts
      .option[String]("redis-password", "Redis password")
      .map(RedisPassword.apply)
      .orNone
  private val masterSet: Opts[Option[RedisMasterSet]] =
    Opts
      .option[String]("redis-masterset", "Redis masterset")
      .map(RedisMasterSet.apply)
      .orNone
  private val refreshInterval: Opts[FiniteDuration] =
    Opts
      .option[FiniteDuration]("redis-connection-refresh", "Redis connection refresh")
      .withDefault(1 hour)

  val configOpts: Opts[RedisConfig] =
    (host, port, sentinel, db.map(Option(_)), password, masterSet, refreshInterval)
      .mapN(RedisConfig.apply)

sealed private trait Provider
private object Provider:
  val configOpts: Opts[List[Provider]] =
    (GitLab.configOpts.orNone, RandommerIO.configOpts.orNone)
      .mapN((gl, rnd) => List(gl, rnd).flatten)

  final case class GitLab(uri: Uri) extends Provider
  object GitLab:

    private val gitLab =
      Opts.flag("gitlab", "GitLab provider")

    private val uri: Opts[Uri] =
      Opts
        .option[String]("gitlab-url", "GitLab url")
        .validate("`gitlab-url` must be given")(_.trim.nonEmpty)
        .map(Uri.unsafeFromString)

    val configOpts: Opts[GitLab] =
      (gitLab, uri).mapN((_, url) => GitLab(url))

  final case class RandommerIO(apiKey: String) extends Provider
  object RandommerIO:

    private val randommerIO =
      Opts.flag("randommerio", "Randommer.io provider")

    private val apiKey: Opts[String] =
      Opts
        .option[String]("randommerIO-api-key", "User API key on randommer.io.")
        .validate("`randommerIO-api-key` must be given")(_.trim.nonEmpty)

    val configOpts: Opts[RandommerIO] =
      (randommerIO, apiKey).mapN((_, apiKey) => RandommerIO(apiKey))
