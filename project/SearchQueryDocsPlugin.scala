import sbt._
import java.nio.file.Path

object SearchQueryDocsPlugin extends AutoPlugin {

  object autoImport {
    val Docs = config("docs")

    val docDirectory = settingKey[File]("The directory containing doc sources")
    val outputDirectory = settingKey[File]("The directory to place processed files")
    val makeManualFile = taskKey[Unit]("Generate doc file")

  }
  import autoImport._

  override def projectConfigurations: Seq[Configuration] =
    Seq(Docs)

  override def projectSettings =
    inConfig(Docs)(Defaults.configSettings) ++ Seq(
      docDirectory := (Compile / Keys.baseDirectory).value / "docs",
      outputDirectory := (Compile / Keys.resourceManaged).value / "query-manual",
      Keys.libraryDependencies ++= Seq(
        "org.scalameta" %% "mdoc" % Dependencies.V.sbtMdoc % Docs,
        "org.scala-lang" %% "scala3-compiler" % Dependencies.V.scala % Docs
      ),
      makeManualFile := Def.taskDyn {
        val cp = (Compile / Keys.dependencyClasspath).value
        val cpArg = cp.files.mkString(java.io.File.pathSeparator)
        val in = docDirectory.value
        val out = outputDirectory.value
        IO.createDirectory(out)

        val options = List(
          // "--verbose",
          "--classpath",
          cpArg,
          "--in",
          in,
          "--out",
          out
        ).mkString(" ")

        (Docs / Keys.runMain).toTask(s" mdoc.SbtMain $options")
      }.value,
      Compile / Keys.resourceGenerators += Def.task {
        val _ = makeManualFile.value
        val out = outputDirectory.value
        (out ** "*.md").get
      },
      Keys.watchSources += Watched.WatchSource(
        docDirectory.value,
        FileFilter.globFilter("*.md"),
        HiddenFileFilter
      )
    )
}
