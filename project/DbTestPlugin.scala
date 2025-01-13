import sbt._
import sbt.Keys._
import _root_.io.renku.servers._

object DbTestPlugin extends AutoPlugin {

  object autoImport {
    val dbTests = taskKey[Unit]("Run the tests with databases turned on")
  }

  import autoImport._

  // AllRequirements makes it enabled on all sub projects by default
  // It is possible to use `.disablePlugins(DbTestPlugin)` to disable
  // it
  override def trigger = PluginTrigger.AllRequirements

  override def projectSettings: Seq[Def.Setting[_]] = Seq(
    Test / dbTests :=
      Def
        .sequential(
          Def.task {
            val logger = streams.value.log
            logger.info("Starting REDIS server")
            RedisServer.start()
            logger.info("Starting SOLR server")
            SolrServer.start()
            logger.info("Running tests")
          },
          (Test / test).all(ScopeFilter(inAggregates(ThisProject))),
          Def.task {
            val logger = streams.value.log
            logger.info("Stopping SOLR server")
            SolrServer.forceStop()
            logger.info("Stopping REDIS server")
            RedisServer.forceStop()
          }
        )
        .value,
    // We need to disable running the `dbTests` on all aggregates,
    // otherwise it would try starting/stopping servers again and
    // again. The `all(ScopeFilter(inAggregates(ThisProject)))` makes
    // sure that tests run on all aggregates anyways- but
    // starting/stopping servers only once.
    dbTests / aggregate := false
  )
}
