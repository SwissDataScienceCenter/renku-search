package io.renku.search

import io.renku.search.logging.LoggingSetup

trait LoggingConfigure extends munit.Suite:

  def defaultVerbosity: Int = 0

  override def beforeAll(): Unit =
    setLoggingVerbosity(defaultVerbosity)
    super.beforeAll()

  def setLoggingVerbosity(level: Int): Unit =
    LoggingSetup.doConfigure(level)

  def withVerbosity[T](level: Int)(body: => T): T =
    val verbosity = defaultVerbosity
    LoggingSetup.doConfigure(level)
    try body
    finally LoggingSetup.doConfigure(verbosity)
