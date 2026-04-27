inThisBuild(
  List(
    organization := "io.github.jciech",
    homepage := Some(url("https://github.com/jciech/tineola")),
    licenses := List("Apache-2.0" -> url("https://www.apache.org/licenses/LICENSE-2.0")),
    developers := List(
      Developer("jciech", "jciech", "noreply@github.com", url("https://github.com/jciech"))
    )
  )
)

lazy val scala213 = "2.13.18"
lazy val scala3 = "3.8.3"

ThisBuild / scalaVersion := scala3
ThisBuild / crossScalaVersions := Seq(scala213, scala3)

lazy val commonSettings = Seq(
  scalacOptions ++= {
    CrossVersion.partialVersion(scalaVersion.value) match {
      case Some((3, _)) =>
        Seq("-deprecation", "-feature", "-unchecked", "-Wunused:all")
      case Some((2, 13)) =>
        Seq("-deprecation", "-feature", "-unchecked", "-Xsource:3", "-Wunused")
      case _ => Nil
    }
  }
)

lazy val vectorModuleSettings = Seq(
  javacOptions ++= Seq("--add-modules", "jdk.incubator.vector"),
  scalacOptions += "-J--add-modules=jdk.incubator.vector",
  Compile / fork := true,
  Test / fork := true,
  Compile / javaOptions += "--add-modules=jdk.incubator.vector",
  Test / javaOptions += "--add-modules=jdk.incubator.vector"
)

lazy val root = (project in file("."))
  .aggregate(core, bench)
  .settings(
    publish / skip := true,
    crossScalaVersions := Nil
  )

lazy val core = (project in file("core"))
  .settings(commonSettings)
  .settings(vectorModuleSettings)
  .settings(
    name := "tineola",
    libraryDependencies ++= Seq(
      "org.scalameta" %% "munit" % "1.2.4" % Test,
      "org.scalameta" %% "munit-scalacheck" % "1.3.0" % Test
    )
  )

lazy val bench = (project in file("bench"))
  .dependsOn(core)
  .enablePlugins(JmhPlugin)
  .settings(commonSettings)
  .settings(vectorModuleSettings)
  .settings(
    publish / skip := true,
    crossScalaVersions := Seq(scala3),
    Jmh / javaOptions += "--add-modules=jdk.incubator.vector",
    libraryDependencies ++= Seq(
      "com.hankcs" % "aho-corasick-double-array-trie" % "1.2.3",
      "org.ahocorasick" % "ahocorasick" % "0.6.3"
    )
  )
