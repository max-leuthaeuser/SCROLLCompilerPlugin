name := "SCROLLCompilerPlugin"
scalaVersion := "2.11.7"
version := "0.0.1"
organization := "com.github.max-leuthaeuser"

javacOptions in Compile ++= Seq("-source", "1.8", "-target", "1.8")

scalacOptions := Seq(
  "-unchecked",
  "-deprecation",
  "-feature",
  "-target:jvm-1.8",
  "-encoding", "utf8",
  "-Xfatal-warnings",
  "-Xlint",
  "-Xlint:-missing-interpolator",
  "-Yno-adapted-args",
  "-Ywarn-numeric-widen",
  "-Ywarn-value-discard",
  "-Xfuture",
  "-Ywarn-unused-import")

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

publishTo := {
  val nexus = "https://oss.sonatype.org/"
  if (isSnapshot.value)
    Some("snapshots" at nexus + "content/repositories/snapshots")
  else
    Some("releases" at nexus + "service/local/staging/deploy/maven2")
}
publishMavenStyle := true
publishArtifact in Test := false
pomIncludeRepository := { _ => false }
pomExtra :=
  <url>https://github.com/max-leuthaeuser/SCROLLCompilerPlugin</url>
    <licenses>
      <license>
        <name>LGPL 3.0 license</name>
        <url>http://www.opensource.org/licenses/lgpl-3.0.html</url>
        <distribution>repo</distribution>
      </license>
    </licenses>
    <scm>
      <connection>scm:git:github.com/max-leuthaeuser/SCROLL.git</connection>
      <developerConnection>scm:git:git@github.com:max-leuthaeuser/SCROLL.git</developerConnection>
      <url>github.com/max-leuthaeuser/SCROLL</url>
    </scm>
    <developers>
      <developer>
        <id>max-leuthaeuser</id>
        <name>Max Leuthaeuser</name>
        <url>https://wwwdb.inf.tu-dresden.de/rosi/investigators/doctoral-students/</url>
      </developer>
    </developers>