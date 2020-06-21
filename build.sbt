name := "akka-apps"

version := "0.1"

scalaVersion := "2.13.2"

lazy val akkaVersion = "2.6.6"

libraryDependencies ++= Seq(
  "com.typesafe.akka" %% "akka-actor-typed" % akkaVersion,
  "com.typesafe.akka" %% "akka-cluster-typed" % akkaVersion,
  "com.typesafe.akka" %% "akka-cluster-sharding-typed" % akkaVersion,
  "com.typesafe.akka" %% "akka-remote" % akkaVersion,
  "com.typesafe.akka" %% "akka-testkit" % akkaVersion,
  "com.typesafe.akka" %% "akka-persistence-typed" % akkaVersion,

  // akka test kit
  "com.typesafe.akka" %% "akka-multi-node-testkit" % akkaVersion % Test,
  "com.typesafe.akka" %% "akka-actor-testkit-typed" % akkaVersion % Test,

  "org.scalatest" %% "scalatest" % "3.0.8" % Test,

  // aeron
  "io.aeron" % "aeron-driver" % "1.27.0",
  "io.aeron" % "aeron-client" % "1.27.0",

  "ch.qos.logback" % "logback-classic" % "1.2.3"
)
