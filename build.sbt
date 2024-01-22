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

organization := "io.renku"
name := "renku-search"
ThisBuild / scalaVersion := "3.3.1"

// This project contains nothing to package, like pure POM maven project
packagedArtifacts := Map.empty

releaseVersionBump := sbtrelease.Version.Bump.Minor
releaseIgnoreUntrackedFiles := true
releaseTagName := (ThisBuild / version).value

addCommandAlias("ci", "; lint; test; publishLocal")
addCommandAlias(
  "lint",
  "; scalafmtSbtCheck; scalafmtCheckAll;" // Compile/scalafix --check; Test/scalafix --check
)
addCommandAlias("fix", "; scalafmtSbt; scalafmtAll") // ; Compile/scalafix; Test/scalafix

lazy val root = project
  .in(file("."))
  .withId("renku-search")
  .settings(
    publish / skip := true,
    publishTo := Some(
      Resolver.file("Unused transient repository", file("target/unusedrepo"))
    )
  )
  .aggregate(
    commons,
    messages,
    redisClient,
    searchProvision
  )

lazy val commons = project
  .in(file("modules/commons"))
  .settings(commonSettings)
  .settings(
    name := "commons",
    libraryDependencies ++=
      Dependencies.catsCore ++
        Dependencies.catsEffect ++
        Dependencies.fs2Core ++
        Dependencies.scodecBits ++
        Dependencies.scribe
  )
  .enablePlugins(AutomateHeaderPlugin)

lazy val redisClient = project
  .in(file("modules/redis-client"))
  .withId("redis-client")
  .settings(commonSettings)
  .settings(
    name := "redis-client",
    Test / testOptions += Tests.Setup(RedisServer.start),
    Test / testOptions += Tests.Cleanup(RedisServer.stop),
    libraryDependencies ++=
      Dependencies.catsCore ++
        Dependencies.catsEffect ++
        Dependencies.redis4Cats ++
        Dependencies.redis4CatsStreams
  )
  .enablePlugins(AutomateHeaderPlugin)

lazy val avroCodec = project
  .in(file("modules/avro-codec"))
  .settings(commonSettings)
  .settings(
    name := "avro-codecs",
    libraryDependencies ++=
      Dependencies.avro ++
        Dependencies.scodecBits
  )

lazy val messages = project
  .in(file("modules/messages"))
  .settings(commonSettings)
  .settings(
    name := "messages",
    libraryDependencies ++= Dependencies.avro,
    Compile / avroScalaCustomTypes := {
      avrohugger.format.SpecificRecord.defaultTypes.copy(
        record = avrohugger.types.ScalaCaseClassWithSchema
      )
    },
    Compile / avroScalaSpecificCustomTypes := {
      avrohugger.format.SpecificRecord.defaultTypes.copy(
        record = avrohugger.types.ScalaCaseClassWithSchema
      )
    },
    Compile / sourceGenerators += (Compile / avroScalaGenerate).taskValue
  )
  .dependsOn(
    commons % "compile->compile;test->test",
    avroCodec % "compile->compile;test->test"
  )
  .enablePlugins(AutomateHeaderPlugin)

lazy val searchProvision = project
  .in(file("modules/search-provision"))
  .withId("search-provision")
  .settings(commonSettings)
  .settings(
    name := "search-provision",
    Test / testOptions += Tests.Setup(RedisServer.start),
    Test / testOptions += Tests.Cleanup(RedisServer.stop)
  )
  .dependsOn(
    commons % "compile->compile;test->test",
    messages % "compile->compile;test->test",
    avroCodec % "compile->compile;test->test",
    redisClient % "compile->compile;test->test"
  )
  .enablePlugins(AutomateHeaderPlugin)

lazy val commonSettings = Seq(
  organization := "io.renku",
  publish / skip := true,
  publishTo := Some(
    Resolver.file("Unused transient repository", file("target/unusedrepo"))
  ),
  Compile / packageDoc / publishArtifact := false,
  Compile / packageSrc / publishArtifact := false,
  // format: off
  scalacOptions ++= Seq(
    "-language:postfixOps", // enabling postfixes
    "-deprecation", // Emit warning and location for usages of deprecated APIs.
    "-encoding", "utf-8", // Specify character encoding used by source files.
    "-explaintypes", // Explain type errors in more detail.
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
  libraryDependencies ++= (
      Dependencies.scribe
    ),
  libraryDependencies ++= (
      Dependencies.catsEffectMunit ++
        Dependencies.scalacheckEffectMunit
    ).map(_ % Test),
  // Format: on
  organizationName := "Swiss Data Science Center (SDSC)",
  startYear := Some(java.time.LocalDate.now().getYear),
  licenses += ("Apache-2.0", new URI(
    "https://www.apache.org/licenses/LICENSE-2.0.txt"
  ).toURL),
  headerLicense := Some(
    HeaderLicense.Custom(
      s"""|Copyright ${java.time.LocalDate.now().getYear} Swiss Data Science Center (SDSC)
          |A partnership between École Polytechnique Fédérale de Lausanne (EPFL) and
          |Eidgenössische Technische Hochschule Zürich (ETHZ).
          |
          |Licensed under the Apache License, Version 2.0 (the "License");
          |you may not use this file except in compliance with the License.
          |You may obtain a copy of the License at
          |
          |    http://www.apache.org/licenses/LICENSE-2.0
          |
          |Unless required by applicable law or agreed to in writing, software
          |distributed under the License is distributed on an "AS IS" BASIS,
          |WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
          |See the License for the specific language governing permissions and
          |limitations under the License.""".stripMargin
    )
  )
)
