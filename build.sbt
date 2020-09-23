name := "akka-apps"

version := "0.1"

scalaVersion := "2.13.2"

lazy val akkaVersion = "2.6.6"
lazy val jacksonVersion  = "3.6.6"
lazy val kryoVersion  = "1.1.5"

libraryDependencies ++= Seq(
  //shapeless
  "com.chuusai" %% "shapeless" % "2.3.3",
  // akka
  "com.typesafe.akka" %% "akka-actor-typed" % akkaVersion,
  "com.typesafe.akka" %% "akka-cluster-typed" % akkaVersion,
  "com.typesafe.akka" %% "akka-cluster-sharding-typed" % akkaVersion,
  "com.typesafe.akka" %% "akka-persistence-typed" % akkaVersion,
  // kryo serializer
  "io.altoo" %% "akka-kryo-serialization" % kryoVersion,
  // local levelDB stores
  "org.fusesource.leveldbjni" % "leveldbjni-all" % "1.8",
  // akka test kit
  "com.typesafe.akka" %% "akka-remote" % akkaVersion % Test,
  "com.typesafe.akka" %% "akka-testkit" % akkaVersion % Test,
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
