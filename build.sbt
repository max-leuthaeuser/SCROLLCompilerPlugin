name := "SCROLLCompilerPlugin"

scalaVersion := "3.8.3"
version := "0.1.0"
organization := "com.github.max-leuthaeuser"

Compile / javacOptions ++= Seq("--release", "17")

scalacOptions ++= Seq(
  "-unchecked",
  "-deprecation",
  "-feature",
  "-encoding",
  "utf8"
)

libraryDependencies ++= Seq(
  "com.github.max-leuthaeuser" %% "scroll" % "3.4",
  "org.scala-lang" %% "scala3-compiler" % scalaVersion.value,
  "org.scalatest" %% "scalatest" % "3.2.20" % Test
)

Test / scalacOptions ++= {
  val pluginJar = (Compile / packageBin).value
  Seq(s"-Xplugin:${pluginJar.getAbsolutePath}")
}
