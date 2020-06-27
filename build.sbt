name := "akka-apps"

version := "0.1"

scalaVersion := "2.13.2"

lazy val akkaVersion = "2.6.6"
lazy val jacksonVersion  = "3.6.6"
lazy val kryoVersion  = "1.1.5"

libraryDependencies ++= Seq(
  "com.typesafe.akka" %% "akka-actor-typed" % akkaVersion,
  "com.typesafe.akka" %% "akka-cluster-typed" % akkaVersion,
  "com.typesafe.akka" %% "akka-cluster-sharding-typed" % akkaVersion,
  "com.typesafe.akka" %% "akka-remote" % akkaVersion,
  "com.typesafe.akka" %% "akka-testkit" % akkaVersion,
  "com.typesafe.akka" %% "akka-persistence-typed" % akkaVersion,
  // json serializer
  "com.typesafe.akka" %% "akka-serialization-jackson" % akkaVersion,
  // kryo serializer
  "io.altoo" %% "akka-kryo-serialization" % kryoVersion,
  // json serializer
  "org.json4s" %% "json4s-jackson" % jacksonVersion,
  "org.json4s" %% "json4s-core" % jacksonVersion,
  // akka persistence for jdbc
  "com.lightbend.akka" %% "akka-persistence-jdbc" % "4.0.0",
  "com.typesafe.akka" %% "akka-persistence-query" % akkaVersion,
  // akka test kit
  "com.typesafe.akka" %% "akka-multi-node-testkit" % akkaVersion % Test,
  "com.typesafe.akka" %% "akka-actor-testkit-typed" % akkaVersion % Test,
  "org.scalatest" %% "scalatest" % "3.2.0" % Test,
  "org.scalamock" %% "scalamock" % "4.4.0" % Test,
  // aeron
  "io.aeron" % "aeron-driver" % "1.28.2",
  "io.aeron" % "aeron-client" % "1.28.2",
  "ch.qos.logback" % "logback-classic" % "1.2.3"
)

lazy val root = (project in file("."))
  .enablePlugins(MultiJvmPlugin)
  .configs(MultiJvm)
