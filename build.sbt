ThisBuild / scalaVersion := "3.8.3"
ThisBuild / version              := "0.0.5"
ThisBuild / versionScheme        := Some("semver-spec")
ThisBuild / organization         := "com.github.max-leuthaeuser"
ThisBuild / organizationName     := "SCROLLCompilerPlugin"
ThisBuild / organizationHomepage := Some(url("https://github.com/max-leuthaeuser/SCROLLCompilerPlugin"))
ThisBuild / description          := "Scala 3 compiler plugin for SCROLL dynamic trait lookup."

ThisBuild / scmInfo := Some(
  ScmInfo(
    url("https://github.com/max-leuthaeuser/SCROLLCompilerPlugin"),
    "scm:git:github.com/max-leuthaeuser/SCROLLCompilerPlugin.git"
  )
)

ThisBuild / homepage := Some(url("https://github.com/max-leuthaeuser/SCROLLCompilerPlugin"))
ThisBuild / licenses := List("LGPL 3.0 license" -> url("http://www.opensource.org/licenses/lgpl-3.0.html"))

ThisBuild / developers := List(
  Developer(
    "max-leuthaeuser",
    "Max Leuthaeuser",
    "max.leuthaeuser@tu-dresden.de",
    url("https://wwwdb.inf.tu-dresden.de/rosi/investigators/doctoral-students/")
  )
)

ThisBuild / pomIncludeRepository := { _ => false }
ThisBuild / publishMavenStyle    := true

ThisBuild / publishTo := {
  val centralSnapshots = "https://central.sonatype.com/repository/maven-snapshots/"
  if (isSnapshot.value) Some("central-snapshots" at centralSnapshots)
  else localStaging.value
}

name := "SCROLLCompilerPlugin"

javacOptions ++= Seq("--release", "11")

scalacOptions ++= Seq(
  "-unchecked",
  "-deprecation",
  "-feature",
  "-language:reflectiveCalls",
  "-language:implicitConversions",
  "-language:postfixOps",
  "-encoding",
  "utf8",
  "-Wunused:imports"
)

libraryDependencies ++= Seq(
  "com.github.max-leuthaeuser" %% "scroll"          % "3.4",
  "com.typesafe"                 % "config"          % "1.4.3",
  "org.scala-lang"               %% "scala3-compiler" % scalaVersion.value % "provided",
  "org.scalatest"                %% "scalatest"       % "3.2.20" % Test
)

assembly / test := {}

assemblyPackageScala / assembleArtifact := false

assemblyMergeStrategy := {
  case "module-info.class" => MergeStrategy.discard
  case PathList("META-INF", "versions", _, "module-info.class") => MergeStrategy.discard
  case PathList("META-INF", "versions", _, "OSGI-INF", _)       => MergeStrategy.discard
  case PathList(ps @ _*) if ps.last.contains(".crom")            => MergeStrategy.discard
  case PathList(ps @ _*) if ps.last.contains(".ecore")           => MergeStrategy.first
  case PathList(ps @ _*) if ps.last == "application.conf"      => MergeStrategy.discard
  case PathList(ps @ _*) if ps.last == "plugin.properties"      => MergeStrategy.first
  case PathList(ps @ _*) if ps.last == "scalac-plugin.xml"      => MergeStrategy.discard
  case PathList(ps @ _*)
      if ps.last.endsWith("plugin.xml") ||
        ps.last.endsWith("properties") ||
        ps.last.endsWith(".exsd") =>
    MergeStrategy.discard
  case x =>
    val oldStrategy = (assembly / assemblyMergeStrategy).value
    oldStrategy(x)
}

Compile / console / scalacOptions ++= (Compile / assembly).map(pluginJar => Seq("-Xplugin:" + pluginJar)).value

Test / scalacOptions ++= (Compile / assembly).map { pluginJar =>
  Seq("-Xplugin:" + pluginJar, "-Jdummy=" + pluginJar.lastModified)
}.value

Test / initialize := {
  val _ = (Test / initialize).value
  System.setProperty("config.file", "src/test/resources/application.conf")
}

Compile / assembly / artifact := {
  val art = (Compile / assembly / artifact).value
  art.withName("assembly")
}

addArtifact(Compile / assembly / artifact, assembly)

Test / publishArtifact := false
