name := "supervisor-strategies-examples"

version := "1.0"

scalaVersion := "2.12.8"

lazy val akkaVersion = "2.5.22"

fork := true

libraryDependencies ++= Seq(
  "com.typesafe.akka" %% "akka-actor" % akkaVersion,
  "com.typesafe.akka" %% "akka-testkit" % akkaVersion,
  "org.scalatest" %% "scalatest" % "3.0.5" % "test"
)
