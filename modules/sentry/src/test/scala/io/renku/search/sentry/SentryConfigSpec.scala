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

import munit.*
import scala.jdk.CollectionConverters.*

class SentryConfigSpec extends FunSuite:
  val dsn: SentryDsn = SentryDsn.unsafeFromString("some-dsn")

  test("disabled returns no options"):
    val result =
      SentryConfig.disabled.withTag(TagName.service, TagValue.searchApi).toSentryOptions
    assertEquals(result, None)

  test("enabled has correct options set"):
    val cfg = SentryConfig
      .enabled(dsn, SentryEnv.dev)
      .withTag(TagName.service, TagValue.searchApi)
    val result = cfg.toSentryOptions.get
    assertEquals(result.getDsn(), dsn.value)
    assertEquals(result.getEnvironment(), SentryEnv.dev.value)
    assertEquals(result.getRelease(), cfg.release)
    assertEquals(
      result.getTags().asScala.toMap,
      cfg.tags.view.map(t => (t._1.value, t._2.value)).toMap
    )

  test("tag name"):
    assertEquals(TagName.service.value, "service")
    val good =
      List(TagName.from("abc"), TagName.from("a1b_c"), TagName.from("_one-two:1.2.3"))
    val bad = List(
      TagName.from(""),
      TagName.from("a b c"),
      TagName.from("/a/path"),
      TagName.from(List.fill(33)("v").mkString)
    )
    good.foreach(_.fold(sys.error, _ => ()))
    bad.foreach(r => assert(r.isLeft, s"unexpected valid TagName: $r"))

  test("tag value"):
    assertEquals(TagValue.searchApi.value, "search-api")
    assertEquals(TagValue.searchProvision.value, "search-provision")
    val good = List(
      TagValue.from("a value"),
      TagValue.from("a longe value is also ok with crazy chars :>=<]-(/{}"),
      TagValue.from("    this    ")
    )
    val bad = List(
      TagValue.from(""),
      TagValue.from("a\nb"),
      TagValue.from(List.fill(201)("v").mkString)
    )
    good.foreach(_.fold(sys.error, _ => ()))
    bad.foreach(r => assert(r.isLeft, s"unexpected valid TagValue: $r"))
