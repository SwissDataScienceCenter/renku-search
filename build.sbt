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
import com.github.sbt.git.SbtGit.GitKeys._

organization := "io.renku"
name := "renku-search"
ThisBuild / scalaVersion := Dependencies.V.scala

// This project contains nothing to package, like pure POM maven project
packagedArtifacts := Map.empty

releaseVersionBump := sbtrelease.Version.Bump.Minor
releaseIgnoreUntrackedFiles := true
releaseTagName := (ThisBuild / version).value

addCommandAlias(
  "ci",
  "readme/readmeCheckModification; lint; compile; Test/compile; dbTests; readme/readmeUpdate; publishLocal; search-provision/Docker/publishLocal; search-api/Docker/publishLocal"
)
addCommandAlias(
  "lint",
  "; scalafmtSbtCheck; scalafmtCheckAll; scalafixAll --check"
)
addCommandAlias(
  "fix",
  "; scalafmtSbt; scalafmtAll; scalafixAll"
)

val writeVersion = taskKey[Unit]("Write version into a file for CI to pick up")

lazy val root = project
  .in(file("."))
  .withId("renku-search")
  .enablePlugins(DbTestPlugin)
  .disablePlugins(RevolverPlugin)
  .settings(
    publish / skip := true,
    publishTo := Some(
      Resolver.file("Unused transient repository", file("target/unusedrepo"))
    ),
    writeVersion := {
      val out = (LocalRootProject / target).value / "version.txt"
      val versionStr = version.value
      IO.write(out, versionStr)
    }
  )
  .aggregate(
    json,
    commons,
    jwt,
    sentry,
    openidKeycloak,
    httpClient,
    events,
    redisClient,
    renkuRedisClient,
    solrClient,
    searchQuery,
    searchSolrClient,
    searchProvision,
    searchApi,
    searchCli
  )

lazy val json = project
  .in(file("modules/json"))
  .settings(commonSettings)
  .settings(
    name := "json",
    // please don't add more dependencies here
    libraryDependencies ++= Dependencies.borer,
    description := "Utilities around working with borer"
  )
  .disablePlugins(DbTestPlugin, RevolverPlugin)

lazy val commons = project
  .in(file("modules/commons"))
  .settings(commonSettings)
  .settings(
    name := "commons",
    libraryDependencies ++=
      Dependencies.borer ++
        Dependencies.catsCore ++
        Dependencies.catsEffect ++
        Dependencies.fs2Core ++
        Dependencies.scodecBits ++
        Dependencies.scribe,
    Test / sourceGenerators += Def.task {
      val sourceDir =
        (LocalRootProject / baseDirectory).value / "project"
      val sources = Seq(
        sourceDir / "RedisServer.scala",
        sourceDir / "SolrServer.scala"
      ) // IO.listFiles(sourceDir)
      val targetDir = (Test / sourceManaged).value / "servers"
      IO.createDirectory(targetDir)

      val targets = sources.map(s => targetDir / s.name)
      IO.copy(sources.zip(targets))
      targets
    }.taskValue,
    buildInfoKeys := Seq(name, version, gitHeadCommit, gitDescribedVersion),
    buildInfoPackage := "io.renku.search"
  )
  .dependsOn(json % "compile->compile;test->test")
  .enablePlugins(BuildInfoPlugin)
  .disablePlugins(DbTestPlugin, RevolverPlugin)

lazy val sentry = project
  .in(file("modules/sentry"))
  .enablePlugins(BuildInfoPlugin)
  .disablePlugins(DbTestPlugin, RevolverPlugin)
  .settings(commonSettings)
  .settings(
    name := "sentry",
    description := "sentry integration with scribe",
    libraryDependencies ++= Dependencies.sentry ++
      Dependencies.scribe,
    buildInfoKeys := Seq(name, version, gitHeadCommit, gitDescribedVersion),
    buildInfoPackage := "io.renku.search.sentry"
  )

lazy val jwt = project
  .in(file("modules/jwt"))
  .disablePlugins(DbTestPlugin, RevolverPlugin)
  .settings(commonSettings)
  .settings(
    name := "jwt",
    description := "jwt with borer",
    libraryDependencies ++= Dependencies.borer ++
      Dependencies.jwtScala ++
      Dependencies.scribe
  )

lazy val http4sBorer = project
  .in(file("modules/http4s-borer"))
  .disablePlugins(DbTestPlugin, RevolverPlugin)
  .withId("http4s-borer")
  .settings(commonSettings)
  .settings(
    name := "http4s-borer",
    description := "Use borer codecs with http4s",
    libraryDependencies ++=
      Dependencies.borer ++
        Dependencies.fs2Core ++
        Dependencies.http4sCore ++
        Dependencies.tapirCore
  )

lazy val httpClient = project
  .in(file("modules/http-client"))
  .withId("http-client")
  .disablePlugins(DbTestPlugin, RevolverPlugin)
  .settings(commonSettings)
  .settings(
    name := "http-client",
    description := "Utilities for the http client in http4s",
    libraryDependencies ++=
      Dependencies.http4sClient ++
        Dependencies.fs2Core ++
        Dependencies.scribe
  )
  .dependsOn(
    http4sBorer % "compile->compile;test->test"
  )

lazy val openidKeycloak = project
  .in(file("modules/openid-keycloak"))
  .disablePlugins(DbTestPlugin, RevolverPlugin)
  .settings(commonSettings)
  .settings(
    name := "openid-keycloak",
    description := "OpenID configuration with keycloak",
    libraryDependencies ++= Dependencies.http4sDsl.map(_ % Test)
  )
  .dependsOn(
    http4sBorer % "compile->compile;test->test",
    httpClient % "compile->compile;test->test",
    jwt % "compile->compile;test->test",
    commons % "compile->compile;test->test"
  )

lazy val http4sMetrics = project
  .in(file("modules/http4s-metrics"))
  .disablePlugins(DbTestPlugin, RevolverPlugin)
  .withId("http4s-metrics")
  .settings(commonSettings)
  .settings(
    name := "http4s-metrics",
    description := "Prometheus metrics for http4s",
    libraryDependencies ++=
      Dependencies.http4sCore ++
        Dependencies.http4sPrometheusMetrics
  )

lazy val http4sCommons = project
  .in(file("modules/http4s-commons"))
  .disablePlugins(DbTestPlugin, RevolverPlugin)
  .withId("http4s-commons")
  .settings(commonSettings)
  .settings(
    name := "http4s-commons",
    description := "Commons for http services",
    libraryDependencies ++=
      Dependencies.http4sCore ++
        Dependencies.http4sDsl ++
        Dependencies.http4sServer ++
        Dependencies.tapirHttp4sServer
  )
  .dependsOn(
    commons % "compile->compile;test->test",
    sentry % "compile->compile;test->test",
    http4sBorer % "compile->compile;test->test"
  )

lazy val redisClient = project
  .in(file("modules/redis-client"))
  .withId("redis-client")
  .settings(commonSettings)
  .settings(
    name := "redis-client",
    libraryDependencies ++=
      Dependencies.catsCore ++
        Dependencies.catsEffect ++
        Dependencies.redis4Cats ++
        Dependencies.redis4CatsStreams ++
        Dependencies.scribe
  )
  .disablePlugins(RevolverPlugin)
  .dependsOn(
    commons % "test->test"
  )

lazy val renkuRedisClient = project
  .in(file("modules/renku-redis-client"))
  .withId("renku-redis-client")
  .settings(commonSettings)
  .settings(
    name := "renku-redis-client",
    libraryDependencies ++=
      Dependencies.catsEffect ++
        Dependencies.redis4Cats ++
        Dependencies.redis4CatsStreams
  )
  .disablePlugins(RevolverPlugin)
  .dependsOn(
    events % "compile->compile;test->test",
    redisClient % "compile->compile;test->test"
  )

lazy val solrClient = project
  .in(file("modules/solr-client"))
  .withId("solr-client")
  .enablePlugins(AvroCodeGen)
  .disablePlugins(RevolverPlugin)
  .settings(commonSettings)
  .settings(
    name := "solr-client",
    libraryDependencies ++=
      Dependencies.catsCore ++
        Dependencies.catsEffect ++
        Dependencies.http4sClient
  )
  .dependsOn(
    httpClient % "compile->compile;test->test",
    json % "compile->compile;test->test",
    commons % "test->test"
  )

lazy val searchSolrClient = project
  .in(file("modules/search-solr-client"))
  .withId("search-solr-client")
  .enablePlugins(AvroCodeGen)
  .disablePlugins(RevolverPlugin)
  .settings(commonSettings)
  .settings(
    name := "search-solr-client",
    libraryDependencies ++=
      Dependencies.catsCore ++
        Dependencies.catsEffect
  )
  .dependsOn(
    avroCodec % "compile->compile;test->test",
    solrClient % "compile->compile;test->test",
    commons % "compile->compile;test->test",
    searchQuery % "compile->compile;test->test"
  )

lazy val avroCodec = project
  .in(file("modules/avro-codec"))
  .disablePlugins(DbTestPlugin, RevolverPlugin)
  .settings(commonSettings)
  .settings(
    name := "avro-codec",
    libraryDependencies ++=
      Dependencies.avro ++
        Dependencies.scodecBits ++
        Dependencies.scribe
  )

lazy val http4sAvro = project
  .in(file("modules/http4s-avro"))
  .disablePlugins(DbTestPlugin, RevolverPlugin)
  .withId("http4s-avro")
  .settings(commonSettings)
  .settings(
    name := "http4s-avro",
    description := "Avro codecs for http4s",
    libraryDependencies ++=
      Dependencies.http4sCore ++
        Dependencies.fs2Core
  )
  .dependsOn(
    avroCodec % "compile->compile;test->test"
  )

lazy val events = project
  .in(file("modules/events"))
  .settings(commonSettings)
  .settings(
    name := "events"
  )
  .dependsOn(
    commons % "compile->compile;test->test",
    avroCodec % "compile->compile;test->test"
  )
  .enablePlugins(AvroSchemaDownload)
  .disablePlugins(DbTestPlugin, RevolverPlugin)

lazy val configValues = project
  .in(file("modules/config-values"))
  .withId("config-values")
  .disablePlugins(RevolverPlugin)
  .settings(commonSettings)
  .settings(
    name := "config-values",
    libraryDependencies ++= Dependencies.ciris
  )
  .dependsOn(
    commons % "compile->compile;test->test",
    sentry % "compile->compile;test->test",
    events % "compile->compile;test->test",
    http4sCommons % "compile->compile;test->test",
    renkuRedisClient % "compile->compile;test->test",
    searchSolrClient % "compile->compile;test->test",
    openidKeycloak % "compile->compile;test->test"
  )

lazy val searchQuery = project
  .in(file("modules/search-query"))
  .withId("search-query")
  .disablePlugins(RevolverPlugin)
  .settings(commonSettings)
  .settings(
    name := "search-query",
    libraryDependencies ++= Dependencies.catsParse ++
      Dependencies.borer
  )
  .dependsOn(
    commons % "compile->compile;test->test"
  )

lazy val searchQueryDocs = project
  .in(file("modules/search-query-docs"))
  .withId("search-query-docs")
  .disablePlugins(RevolverPlugin)
  .settings(commonSettings)
  .enablePlugins(SearchQueryDocsPlugin)
  .settings(
    name := "search-query-docs"
  )
  .dependsOn(searchQuery)

lazy val searchProvision = project
  .in(file("modules/search-provision"))
  .withId("search-provision")
  .settings(commonSettings)
  .settings(
    name := "search-provision",
    libraryDependencies ++= Dependencies.ciris
  )
  .dependsOn(
    commons % "compile->compile;test->test",
    sentry % "compile->compile;test->test",
    events % "compile->compile;test->test",
    http4sCommons % "compile->compile;test->test",
    http4sMetrics % "compile->compile;test->test",
    renkuRedisClient % "compile->compile;test->test",
    searchSolrClient % "compile->compile;test->test",
    configValues % "compile->compile;test->test"
  )
  .enablePlugins(DockerImagePlugin, RevolverPlugin)

lazy val searchApi = project
  .in(file("modules/search-api"))
  .withId("search-api")
  .settings(commonSettings)
  .settings(
    name := "search-api",
    libraryDependencies ++=
      Dependencies.ciris ++
        Dependencies.tapirOpenAPI
  )
  .dependsOn(
    commons % "compile->compile;test->test",
    sentry % "compile->compile;test->test",
    http4sBorer % "compile->compile;test->test",
    http4sCommons % "compile->compile;test->test",
    http4sMetrics % "compile->compile;test->test",
    searchSolrClient % "compile->compile;test->test",
    configValues % "compile->compile;test->test",
    searchQueryDocs % "compile->compile;test->test",
    jwt % "compile->compile;test->test",
    openidKeycloak % "compile->compile;test->test"
  )
  .enablePlugins(DockerImagePlugin, RevolverPlugin)

lazy val searchCli = project
  .in(file("modules/search-cli"))
  .enablePlugins(JavaAppPackaging, DockerImagePlugin)
  .disablePlugins(DbTestPlugin, RevolverPlugin)
  .withId("search-cli")
  .settings(commonSettings)
  .settings(
    name := "search-cli",
    description := "A set of CLI tools",
    Compile / run / fork := true,
    libraryDependencies ++=
      Dependencies.decline ++
        Dependencies.http4sClient
  )
  .dependsOn(
    commons % "compile->compile;test->test",
    httpClient % "compile->compile;test->test",
    renkuRedisClient % "compile->compile;test->test",
    searchSolrClient % "compile->compile;test->test",
    configValues % "compile->compile;test->test"
  )

lazy val readme = project
  .in(file("modules/readme"))
  .enablePlugins(ReadmePlugin)
  .settings(commonSettings)
  .settings(
    name := "search-readme",
    scalacOptions :=
      Seq(
        "-feature",
        "-deprecation",
        "-unchecked",
        "-encoding",
        "UTF-8",
        "-language:higherKinds",
        "-Xkind-projector:underscores"
      ),
    readmeAdditionalFiles := Map(
      // copy query manual into source tree for easier discovery on github
      (searchQueryDocs / Docs / outputDirectory).value / "manual.md" -> "query-manual.md"
    )
  )
  .dependsOn(
    renkuRedisClient % "compile->compile;compile->test",
    searchProvision,
    searchApi
  )

lazy val commonSettings = Seq(
  organization := "io.renku",
  publish / skip := true,
  publishTo := Some(
    Resolver.file("Unused transient repository", file("target/unusedrepo"))
  ),
  Compile / packageDoc / publishArtifact := false,
  Compile / packageSrc / publishArtifact := false,
  licenses := Seq(
    "Apache-2.0" -> url("https://spdx.org/licenses/Apache-2.0.html")
  ),
  // format: off
  scalacOptions ++= Seq(
    "-language:postfixOps", // enabling postfixes
    "-deprecation", // Emit warning and location for usages of deprecated APIs.
    "-encoding", "utf-8", // Specify character encoding used by source files.
    //"-explaintypes", // Explain type errors in more detail.
    "-feature", // Emit warning and location for usages of features that should be imported explicitly.
    "-unchecked", // Enable additional warnings where generated code depends on assumptions.
    "-language:higherKinds", // Allow higher-kinded types
    "-language:existentials", // Existential types (besides wildcard types) can be written and inferred
    "-Xfatal-warnings",
    "-Wunused:imports", // Warn if an import selector is not referenced.
    "-Wunused:locals", // Warn if a local definition is unused.
    "-Wunused:explicits", // Warn if an explicit parameter is unused.
    "-Wvalue-discard" // Warn when non-Unit expression results are unused.
  ),
  Compile / console / scalacOptions := (Compile / scalacOptions).value.filterNot(_ == "-Xfatal-warnings"),
  Test / console / scalacOptions := (Compile / console / scalacOptions).value,
  // parallel execution would work, but requires a quite powerful solr server
  Test / parallelExecution := false,
  semanticdbEnabled := true, // enable SemanticDB
  semanticdbVersion := scalafixSemanticdb.revision,
  libraryDependencies ++= (
      Dependencies.munitCatsEffect ++
        Dependencies.scalacheckEffectMunit ++
        Dependencies.catsScalaCheck ++
        Dependencies.scribe
    ).map(_ % Test),
)
