import sbt.*
import sbt.Keys.{libraryDependencies, sourceGenerators}
import sbtavrohugger.SbtAvrohugger
import sbtavrohugger.SbtAvrohugger.autoImport.*

object AvroCodeGen extends AutoPlugin {
  override def requires = SbtAvrohugger

  def avroHuggerSettings = Seq(
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
    }
  )

  override def projectSettings = avroHuggerSettings ++ Seq(
    Compile / sourceGenerators += (Compile / avroScalaGenerate).taskValue
  )
}
