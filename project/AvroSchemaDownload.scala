import sbt._
import sbt.Keys._
import com.github.sbt.git._
import sbtavrohugger.SbtAvrohugger
import sbtavrohugger.SbtAvrohugger.autoImport.*
import java.io.File
import org.eclipse.jgit.api.ResetCommand.ResetType

object AvroSchemaDownload extends AutoPlugin {

  override def requires = GitPlugin && SbtAvrohugger

  object autoImport {
    val schemaRepository = settingKey[String]("The repository to download")
    val schemaRef =
      settingKey[Option[String]]("The branch, tag or commit sha to checkout")
    val schemaTargetDirectory = settingKey[File]("The directory to download into")
    val schemaDownloadRepository = taskKey[Seq[File]]("Download the repository")
    val schemaClearDownload = taskKey[Unit]("Removes all downloaded files")
  }

  import autoImport._

  override def projectSettings = AvroCodeGen.avroHuggerSettings ++ Seq(
    schemaRepository := "https://github.com/SwissDataScienceCenter/renku-search",
    schemaRef := Some("v0.0.1"),
    schemaTargetDirectory := (Compile / target).value / "renku-avro-schemas",
    schemaClearDownload := {
      val target = schemaTargetDirectory.value
      IO.delete(target)
    },
    schemaDownloadRepository := {
      val logger = streams.value.log
      val repo = schemaRepository.value
      val refspec = schemaRef.value
      val output = schemaTargetDirectory.value
      synchronizeSchemaFiles(logger, repo, refspec, output)
      Seq(output)
    },
    Compile / avroSourceDirectories := Seq(
      schemaTargetDirectory.value
    ),
    Compile / sourceGenerators += Def
      .sequential(
        schemaDownloadRepository,
        Compile / avroScalaGenerate
      )
      .taskValue
  )

  def synchronizeSchemaFiles(
      logger: Logger,
      repo: String,
      refspec: Option[String],
      target: File
  ): Unit =
    if (target.exists) updateRepository(logger, target, refspec)
    else cloneRepository(logger, repo, refspec, target)

  def updateRepository(logger: Logger, base: File, refspec: Option[String]) = {
    logger.info(s"Updating schema repository at $base")
    val git = JGit(base)
    git.porcelain.fetch().call()
    switchBranch(logger, git, refspec)
  }

  def cloneRepository(
      logger: Logger,
      repo: String,
      refspec: Option[String],
      target: File
  ): Unit = {
    logger.info(s"Downloading repository $repo to $target")
    val jgit = JGit.clone(repo, target)
    switchBranch(logger, jgit, refspec)
  }

  def switchBranch(logger: Logger, git: JGit, refspec: Option[String]) =
    refspec match {
      case Some(ref)
          if ref != git.branch && !git.currentTags.contains(ref) && !git.headCommitSha
            .contains(ref) =>
        logger.info(s"Changing to $ref")
        val cmd = git.porcelain.reset()
        cmd.setMode(ResetType.HARD)
        cmd.setRef(ref)
        val res = cmd.call()
        logger.info(s"Repository now on $res")

      case _ => ()
    }
}
