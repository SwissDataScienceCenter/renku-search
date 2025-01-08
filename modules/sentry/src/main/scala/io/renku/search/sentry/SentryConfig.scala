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
