import sbt._
import sbt.Keys._
import com.github.sbt.git._
import sbtavrohugger.SbtAvrohugger
import sbtavrohugger.SbtAvrohugger.autoImport.*
import java.io.File

object AvroSchemaDownload extends AutoPlugin {

  override def requires = GitPlugin && SbtAvrohugger

  object autoImport {
    val schemaRepository = settingKey[String]("The repository to download")
    val schemaBranch = settingKey[Option[String]]("The branch to checkout")
    val schemaTargetDirectory = settingKey[File]("The directory to download into")
    val schemaDownloadRepository = taskKey[Seq[File]]("Download the repository")
    val schemaClearDownload = taskKey[Unit]("Removes all downloaded files")
  }

  import autoImport._

  override def projectSettings = AvroCodeGen.avroHuggerSettings ++ Seq(
    schemaRepository := "https://github.com/SwissDataScienceCenter/renku-search",
    schemaBranch := None,
    schemaTargetDirectory := (Compile / target).value / "renku-avro-schemas",
    schemaClearDownload := {
      val target = schemaTargetDirectory.value
      IO.delete(target)
    },
    schemaDownloadRepository := {
      val logger = streams.value.log
      val repo = schemaRepository.value
      val branch = schemaBranch.value
      val output = schemaTargetDirectory.value
      synchronizeSchemaFiles(logger, repo, branch, output)
      Seq(output)
    },

    Compile / avroSourceDirectories := Seq(
      schemaTargetDirectory.value
    ),

    Compile / sourceGenerators += Def.sequential(
      schemaDownloadRepository, Compile / avroScalaGenerate
    ).taskValue
  )

  def synchronizeSchemaFiles(
    logger: Logger,
    repo: String,
    branch: Option[String],
    target: File
  ): Unit =
    if (target.exists) pullRepository(logger, target, branch)
    else cloneRepository(logger, repo, branch, target)


  def pullRepository(logger: Logger, base: File, branch: Option[String]) = {
    logger.info(s"Updating schema repository at $base")
    val git = JGit(base)
    val result = git.porcelain.pull().call()
    if (!result.isSuccessful) {
      val msg = s"The pull from ${git.remoteOrigin} failed!"
      logger.error(msg)
      sys.error(msg)
    }
    switchBranch(logger, git, branch)
  }

  def cloneRepository(logger: Logger, repo: String, branch: Option[String], target: File): Unit = {
    logger.info(s"Downloading repository $repo to $target")
    val jgit = JGit.clone(repo, target)
    switchBranch(logger, jgit, branch)
  }

  //TODO the same for tags
  def switchBranch(logger: Logger, git: JGit, branch: Option[String]) =
    branch match {
      case Some(b) if b != git.branch =>
        logger.info(s"Changing to branch $b")
        git.checkoutBranch(b)

      case _ => ()
    }
}
