name := """foa-challenge"""
organization := "ncats"

version := "1.0-SNAPSHOT"

lazy val root = (project in file(".")).enablePlugins(PlayJava)

scalaVersion := "2.11.11"

libraryDependencies ++= Seq(
  guice,
  "com.typesafe.play" %% "play-json" % "2.6.0",
  "com.typesafe.play" %% "play-ahc-ws-standalone" % "1.0.0",
  "com.typesafe.play" %% "play-ws-standalone-xml" % "1.0.0",
  "com.typesafe.play" %% "play-ws-standalone-json"% "1.0.0",
  "com.typesafe.play" %% "play-mailer" % "6.0.0",
  "com.typesafe.play" %% "play-mailer-guice" % "6.0.0",
  "org.webjars" %% "webjars-play" % "2.6.0",
  "org.webjars" % "bootstrap" % "3.3.7",
  "org.webjars" % "jquery" % "3.2.1"
)
