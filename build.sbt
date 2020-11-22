name := "cats-effect-tutorial"

version := "2.2.0"

scalaVersion := "2.12.8"

libraryDependencies += "org.typelevel" %% "cats-effect" % "2.2.0" withSources() withJavadoc()
libraryDependencies += "com.codecommit" %% "cats-effect-testing-scalatest" % "0.4.2" % Test

scalacOptions ++= Seq(
  "-feature",
  "-deprecation",
  "-unchecked",
  "-language:postfixOps",
  "-language:higherKinds",
  "-Ypartial-unification")