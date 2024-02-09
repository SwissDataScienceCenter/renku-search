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
    schemaRepository := "https://github.com/SwissDataScienceCenter/renku-schema",
    schemaRef := Some("main"),
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
    Compile / avroScalaCustomNamespace := Map("*" -> "io.renku.messages"),
    Compile / avroScalaSpecificCustomNamespace := Map("*" -> "io.renku.messages"),
    Compile / avroSourceDirectories := Seq(
      schemaTargetDirectory.value
    ),
    Compile / sourceGenerators += Def
      .sequential(
        schemaDownloadRepository,
        Compile / avroScalaGenerate,
        Def.task {
          val out = (Compile / avroScalaSource).value
          val pkg = "io.renku.messages"
          val logger = streams.value.log
          evilHackAddPackage(logger, out, pkg)
          Seq.empty[File]
        }
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

  def evilHackAddPackage(logger: Logger, dir: File, pkg: String): Unit = {
    val pkgLine = s"package $pkg"

    def prependPackage(file: File) = {
      val content = IO.read(file)
      if (!content.startsWith(pkgLine)) {
        logger.info(s"Add package to: $file")
        IO.write(file, s"$pkgLine;\n\n") // scala & java ...
        IO.append(file, content)
      }
    }

    (dir ** "*.scala").get().foreach(prependPackage)
    (dir ** "*.java").get().foreach(prependPackage)
  }
}
