inThisBuild(
  List(
    organization := "io.github.jciech",
    homepage     := Some(url("https://github.com/jciech/tineola")),
    licenses     := List("Apache-2.0" -> url("https://www.apache.org/licenses/LICENSE-2.0")),
    developers := List(
      Developer("jciech", "jciech", "noreply@github.com", url("https://github.com/jciech"))
    )
  )
)

lazy val scala213 = "2.13.18"
lazy val scala3   = "3.8.2"

ThisBuild / scalaVersion       := scala3
ThisBuild / crossScalaVersions := Seq(scala213, scala3)

lazy val root = (project in file("."))
  .settings(
    name := "tineola",
    libraryDependencies ++= Seq(
      "org.scalameta" %% "munit" % "1.2.0" % Test
    ),
    scalacOptions ++= {
      CrossVersion.partialVersion(scalaVersion.value) match {
        case Some((3, _)) =>
          Seq(
            "-deprecation",
            "-feature",
            "-unchecked",
            "-Wunused:all",
            "-source:future"
          )
        case Some((2, 13)) =>
          Seq(
            "-deprecation",
            "-feature",
            "-unchecked",
            "-Xsource:3",
            "-Wunused"
          )
        case _ => Nil
      }
    }
  )
