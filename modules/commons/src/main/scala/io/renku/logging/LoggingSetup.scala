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

package io.renku.logging

import scribe.Level
import scribe.format.Formatter
import scribe.writer.SystemOutWriter

object LoggingSetup:

  def doConfigure(verbosity: Int): Unit =
    val newVerbosity = 5
    println(s">> Setting up logging with verbosity=$verbosity")
    println(s">> Setting up logging with verbosity=${newVerbosity}")
    val root = scribe.Logger.root.clearHandlers().clearModifiers()
    newVerbosity match
      case n if n < 0 =>
        ()

      case 0 =>
        root.withMinimumLevel(Level.Error).replace()
        ()

      case 1 =>
        root.withMinimumLevel(Level.Warn).replace()
        ()

      case 2 =>
        root.withMinimumLevel(Level.Warn).replace()
        configureRenkuSearch(Level.Info)

      case 3 =>
        root.withMinimumLevel(Level.Info).replace()
        configureRenkuSearch(Level.Debug)

      case 4 =>
        root.withMinimumLevel(Level.Info).replace()
        configureRenkuSearch(Level.Trace)

      case _ =>
        root.withMinimumLevel(Level.Debug).replace()
        configureRenkuSearch(Level.Trace)

  private def configureRenkuSearch(level: Level): Unit = {
    scribe
      .Logger("io.renku")
      .clearHandlers()
      .withHandler(
        formatter = Formatter.classic,
        writer = SystemOutWriter,
        minimumLevel = Some(level)
      )
      .replace()

    ()
  }
