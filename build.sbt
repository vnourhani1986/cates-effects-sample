name := "cats-effect-tutorial"

version := "2.2.0"

scalaVersion := "2.12.8"

val CirceVersion = "0.12.3"
val Http4sVersion = "0.20.23"
val Specs2Version = "4.1.0"
val LogbackVersion = "1.2.3"

libraryDependencies += "org.typelevel" %% "cats-effect" % "2.2.0" withSources () withJavadoc ()
libraryDependencies += "io.chrisdavenport" %% "cats-effect-time" % "0.1.2"
libraryDependencies += "com.codecommit" %% "cats-effect-testing-scalatest" % "0.4.2" % Test
libraryDependencies ++= Seq(
  "io.circe" %% "circe-core",
  "io.circe" %% "circe-generic",
  "io.circe" %% "circe-parser"
).map(_ % CirceVersion)
libraryDependencies ++= Seq(
  "org.http4s" %% "http4s-blaze-server" % Http4sVersion,
  "org.http4s" %% "http4s-blaze-client" % Http4sVersion,
  "org.http4s" %% "http4s-circe" % Http4sVersion,
  "org.http4s" %% "http4s-dsl" % Http4sVersion,
  "io.circe" %% "circe-generic" % CirceVersion,
  "org.specs2" %% "specs2-core" % Specs2Version % "test",
  "ch.qos.logback" % "logback-classic" % LogbackVersion
)

libraryDependencies += "com.github.pureconfig" %% "pureconfig" % "0.14.0"
libraryDependencies += "com.github.pureconfig" %% "pureconfig-cats-effect" % "0.14.0"

scalacOptions ++= Seq(
  "-feature",
  "-deprecation",
  "-unchecked",
  "-encoding",
  "UTF-8",
  "-Xfatal-warnings",
  "-language:postfixOps",
  "-language:higherKinds",
  "-Ypartial-unification"
)
