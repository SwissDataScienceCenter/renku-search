import com.typesafe.sbt.packager.docker._
import com.typesafe.sbt.packager.Keys._
import com.typesafe.sbt.packager.archetypes.JavaServerAppPackaging
import sbt._
import sbt.Keys._
import com.github.sbt.git.SbtGit.git
import sbtdynver._
import sbtdynver.DynVerPlugin.autoImport._

/** Sets default docker image settings for sbt-native-packager's `DockerPlugin`. */
object DockerImagePlugin extends AutoPlugin {

  // Load DockerPlugin and JavaServerAppPackaging whenever this plugin is enabled
  override def requires = DockerPlugin && JavaServerAppPackaging && DynVerPlugin
  override def trigger = allRequirements

  import DockerPlugin.autoImport._

  val dockerSettings = Seq(
    dockerUpdateLatest := true,
    // dockerEntrypoint    := Seq(s"bin/${executableScriptName.value}", "-Duser.timezone=UTC", "$JAVA_OPTS"),
    dockerAdditionalPermissions += (DockerChmodType.UserGroupWriteExecute, "/opt/docker"),
    dockerBaseImage := s"eclipse-temurin:21-jre",
    // derive a package name
    Docker / packageName := (Compile / name).value,
    dockerRepository := Some("docker.io"),
    dockerUsername := Some("renku"),

    // temporarily use git hash as image tag
    Docker / version :=
      imageTag(dynverGitDescribeOutput.value, git.gitHeadCommit.value)
        .getOrElse((Compile / version).value)
  )

  private def imageTag(out: Option[GitDescribeOutput], headCommit: Option[String]) =
    out match {
      case Some(d) if d.isCleanAfterTag => Some(d.ref.dropPrefix)
      case _                            => headCommit.map(_.take(12))
    }

  override def projectSettings =
    dockerSettings
}
