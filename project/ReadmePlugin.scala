import sbt._
import sbt.Keys._
import mdoc.MdocPlugin
import com.github.sbt.git.{GitPlugin, JGit}
import com.github.sbt.git.SbtGit.GitKeys
import org.eclipse.jgit.api._
import org.eclipse.jgit.diff.DiffEntry
import org.eclipse.jgit.treewalk.CanonicalTreeParser
import scala.jdk.CollectionConverters._

object ReadmePlugin extends AutoPlugin {
  override def requires = MdocPlugin && GitPlugin
  object autoImport {
    val readmeUpdate = inputKey[Unit]("Update the root README.md")
    val readmeBaseRef =
      settingKey[Option[String]]("The base ref to check modification against")
    val readmeCheckModification = taskKey[Unit](
      "Check the diff against the target branch for modifications to generated files"
    )
    val readmeAdditionalFiles = settingKey[Map[File, String]](
      "Additional (generated) files to copy into the root docs/ folder"
    )
  }

  import autoImport._
  import MdocPlugin.autoImport._

  override def projectSettings = Seq(
    libraryDependencies ++= Seq.empty,
    mdocIn := (LocalRootProject / baseDirectory).value / "docs" / "readme.md",
    mdocOut := (LocalRootProject / baseDirectory).value / "README.md",
    fork := true,
    readmeAdditionalFiles := Map.empty,
    readmeBaseRef := sys.env
      .get("README_BASE_REF")
      .map(_.trim)
      .filter(_.nonEmpty)
      .orElse(Some("HEAD"))
      .flatMap {
        case "_skip_" => None
        case ref      => Some(s"${ref}^{tree}")
      },
    readmeCheckModification := {
      val bref = readmeBaseRef.value
      val dir = (LocalRootProject / baseDirectory).value
      val additional = readmeAdditionalFiles.value
      val readmeIn = mdocIn.value
      val readmeOut = mdocOut.value
      val logger = streams.value.log
      bref match {
        case Some(ref) =>
          checkModification(
            logger,
            ref,
            dir,
            readmeIn,
            readmeOut,
            additional,
            dir / "docs"
          )
        case None => logger.info("Skipping readme modification checks")
      }
    },
    readmeUpdate := {
      mdoc.evaluated
      val logger = streams.value.log
      val additional = readmeAdditionalFiles.value
      val addout = (LocalRootProject / baseDirectory).value / "docs"
      additional.toList.foreach { case (src, name) =>
        val dest = addout / name
        logger.info(s"Copying $src -> $dest")
        IO.copyFile(src, dest)
      }
      ()
    }
  )

  def checkModification(
      logger: Logger,
      baseRef: String,
      sourceRoot: File,
      readmeIn: File,
      readmeOut: File,
      additional: Map[File, String],
      additionalOut: File
  ) = {
    val git = JGit(sourceRoot).porcelain
    val repo = git.getRepository
    val base = repo.resolve(baseRef)
    if (base eq null) sys.error(s"Cannot resolve base ref: $baseRef")
    val p = new CanonicalTreeParser()
    val reader = repo.newObjectReader()
    p.reset(reader, base)
    val diff = git
      .diff()
      .setOldTree(p)
      .setShowNameOnly(true)
      .call()
      .asScala
      .filter(e => e.getChangeType == DiffEntry.ChangeType.MODIFY)
      .map(_.getOldPath)
      .toSet

    logger.debug(s"Changed files from $baseRef: $diff")

    val checker = Checker(sourceRoot, diff, logger)

    // check readme
    checker.check(readmeIn, readmeOut)
    // check others
    additional.toList.foreach { case (src, targetName) =>
      val dest = additionalOut / targetName
      checker.check(src, dest)
    }
    ()
  }

  def relativize(base: File, file: File): String =
    base
      .relativize(file)
      .map(_.toString)
      .getOrElse(
        sys.error(
          s"Cannot obtain path into repository for $file"
        )
      )

  final case class Checker(sourceRoot: File, diff: Set[String], logger: Logger) {
    def check(src: File, dest: File) = {
      val srcPath = relativize(sourceRoot, src)
      val destPath = relativize(sourceRoot, dest)
      val srcModified = diff.contains(srcPath)
      val destModified = diff.contains(destPath)
      def modWord(modified: Boolean) = if (modified) "modified" else "not modified"
      logger.debug(
        s"${modWord(srcModified)} $srcPath | ${modWord(destModified)} $destPath"
      )
      logger.info(s"Check modification $srcPath -> $destPath …")
      if (srcModified && !destModified) {
        logger.error(s"You changed $srcPath but did not generate the output file")
        sys.error("Checking failed")
      }
      if (destModified && !srcModified) {
        logger.error(
          s"You changed $destPath but this is a generated file, please change $srcPath instead"
        )
        sys.error("Checking failed")
      }
    }
  }
}
