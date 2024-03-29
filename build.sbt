name := """foa-challenge"""
organization := "ncats"

val buildDate = (new java.text.SimpleDateFormat("yyyyMMdd"))
  .format(new java.util.Date())

version := "%s".format(buildDate)

lazy val root = (project in file(".")).enablePlugins(PlayJava, PlayEbean)

scalaVersion := "2.11.11"

libraryDependencies ++= Seq(
  guice,
  jdbc,
  ws,
  ehcache,
  "com.h2database" % "h2" % "1.4.194",
  "mysql" % "mysql-connector-java" % "5.1.43",
  "com.typesafe.play" %% "play-json" % "2.6.0",
  "com.typesafe.play" %% "play-ahc-ws-standalone" % "1.0.0",
  "com.typesafe.play" %% "play-ws-standalone-xml" % "1.0.0",
  "com.typesafe.play" %% "play-ws-standalone-json"% "1.0.0",
  "com.typesafe.play" %% "play-mailer" % "6.0.0",
  "com.typesafe.play" %% "play-mailer-guice" % "6.0.0",
  "org.webjars" %% "webjars-play" % "2.6.0",
  "org.webjars" % "bootstrap" % "3.3.7",
  "org.webjars" % "jquery" % "3.2.1",
  "org.webjars" % "font-awesome" % "4.7.0",
  "net.sf.opencsv" % "opencsv" % "2.3",
  "org.apache.commons" % "commons-math3" % "3.6.1"
)
