name := "SCROLLCompilerPlugin"
scalaVersion := "2.11.7"
version := "0.0.1"

javacOptions in Compile ++= Seq("-source", "1.8", "-target", "1.8")

scalacOptions := Seq("-unchecked", "-deprecation", "-feature", "-target:jvm-1.8", "-encoding", "utf8")

libraryDependencies ++= Seq(
  ("com.github.max-leuthaeuser" % "scroll_2.11" % "0.9.8").exclude("org.javabits.jgrapht", "jgrapht-core"),
  "com.typesafe" % "config" % "1.3.1",
  "org.scala-lang" % "scala-compiler" % scalaVersion.value,
  "org.scalatest" %% "scalatest" % "2.2.3" % "test"
)

test in assembly := {}

assemblyJarName in assembly := "SCROLLCompilerPlugin.jar"

assembleArtifact in assemblyPackageScala := false

unmanagedResourceDirectories in Compile ++= Seq(
  baseDirectory.value / "src/main/resources",
  baseDirectory.value / "src/test/resources"
)

assemblyMergeStrategy in assembly := {
  case PathList(ps@_*) if ps.last == "application.conf" => MergeStrategy.first
  case PathList(ps@_*) if ps.last == "scalac-plugin.xml" => MergeStrategy.first
  case PathList(ps@_*)
    if ps.last.endsWith("plugin.xml") ||
      ps.last.endsWith("properties") ||
      ps.last.endsWith(".exsd") => MergeStrategy.discard
  case x =>
    val oldStrategy = (assemblyMergeStrategy in assembly).value
    oldStrategy(x)
}

scalacOptions in console in Compile <+= (assembly in Compile) map {
  pluginJar => "-Xplugin:" + pluginJar
}

scalacOptions in Test <++= (assembly in Compile) map {
  pluginJar => Seq("-Xplugin:" + pluginJar, "-Jdummy=" + pluginJar.lastModified)
}