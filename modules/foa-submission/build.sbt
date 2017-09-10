name := """foa-submission"""
organization := "ncats"

val buildDate = (new java.text.SimpleDateFormat("yyyyMMdd"))
  .format(new java.util.Date())

version := "%s".format(buildDate)

lazy val root = (project in file(".")).enablePlugins(PlayJava)

scalaVersion := "2.11.11"

libraryDependencies ++= Seq(
  guice,
  ws
)
