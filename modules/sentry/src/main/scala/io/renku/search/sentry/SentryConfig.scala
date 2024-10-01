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

package io.renku.search.sentry

import io.sentry.SentryOptions
import io.sentry.protocol.SdkVersion

final case class SentryConfig(
    enabled: Boolean,
    dsn: SentryDsn,
    env: SentryEnv,
    serverName: Option[String],
    tags: Map[TagName, TagValue]
):

  def withTag(name: TagName, value: TagValue): SentryConfig =
    copy(tags = tags.updated(name, value))

  def release: String =
    val sha = BuildInfo.gitHeadCommit match
      case Some(c) => s"(#${c.take(8)})"
      case None    => ""
    s"renku-search@${BuildInfo.version}${sha}"

  private[sentry] lazy val toSentryOptions: Option[SentryOptions] =
    Option.when(enabled) {
      val opts = new SentryOptions()
      opts.setDsn(dsn.value)
      opts.setEnvironment(env.value)
      opts.setRelease(release)
      serverName.foreach(opts.setServerName)
      tags.foreach { case (n, v) => opts.setTag(n.value, v.value) }
      val sdk = opts.getSdkVersion()
      opts.setSdkVersion(
        SdkVersion.updateSdkVersion(sdk, "renku-sentry", BuildInfo.version)
      )
      opts
    }

object SentryConfig:
  val disabled: SentryConfig =
    SentryConfig(
      false,
      SentryDsn.unsafeFromString("disabled"),
      SentryEnv.dev,
      None,
      Map.empty
    )
  def enabled(dsn: SentryDsn, env: SentryEnv): SentryConfig =
    SentryConfig(true, dsn, env, None, Map.empty)
