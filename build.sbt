name := "caliban-upload-example"

version := "0.1"

scalaVersion := "2.13.2"

enablePlugins(PlayScala)

libraryDependencies ++= Seq(
  filters,
  "com.github.ghostdogpr" %% "caliban" % "0.8.2",
  "com.github.ghostdogpr" %% "caliban-play"       % "0.8.2",
  "dev.zio" %% "zio" % "1.0.0-RC20",
  "dev.zio" %% "zio-streams" % "1.0.0-RC20"
)
