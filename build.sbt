organization := "com.bizo"

name := "crucible-survivor"

version := "1.0.0"

scalaVersion := "2.10.3"

scalacOptions ++= Seq("-unchecked", "-deprecation", "-language:_")

libraryDependencies ++= Seq(
  "com.bizo" %% "crucible-client-scala" % "1.1.0",
  "com.sun.jersey" % "jersey-bundle" % "1.9.1",
  "javax.ws.rs" % "jsr311-api" % "1.1.1",
  "net.liftweb" % "lift-json_2.10" % "2.5",
  "net.liftweb" % "lift-json-ext_2.10" % "2.5",
  "commons-io" % "commons-io" % "2.4",
  "junit" % "junit" % "4.10" % "test",
  "org.scalatest" %% "scalatest" % "2.2.1" % "test",
  "com.novocode" % "junit-interface" % "0.10-M4" % "test"
)

EclipseKeys.withSource := true
