name := "akka-apps"

version := "0.1"

scalaVersion := "2.13.2"

lazy val akkaVersion = "2.6.6"
lazy val akkaHttpVersion = "10.2.1"
lazy val jacksonVersion = "3.6.6"
lazy val kryoVersion = "1.1.5"

libraryDependencies ++= Seq(
  // logging
  "ch.qos.logback" % "logback-classic" % "1.2.3",
  //shapeless
  "com.chuusai" %% "shapeless" % "2.3.3",
  // akka
  "com.typesafe.akka" %% "akka-stream-typed" % akkaVersion,
  "com.typesafe.akka" %% "akka-actor-typed" % akkaVersion,
  "com.typesafe.akka" %% "akka-cluster-typed" % akkaVersion,
  "com.typesafe.akka" %% "akka-cluster-sharding-typed" % akkaVersion,
  "com.typesafe.akka" %% "akka-persistence-typed" % akkaVersion,
  "com.typesafe.akka" %% "akka-discovery" % akkaVersion,
  // cluster bootstrap
  "com.lightbend.akka.management" %% "akka-management-cluster-http" % "1.0.8",
  "com.lightbend.akka.management" %% "akka-management-cluster-bootstrap" % "1.0.8",
  "com.lightbend.akka.discovery" %% "akka-discovery-kubernetes-api" % "1.0.8",
  // akka http server
  "com.typesafe.akka" %% "akka-http" % akkaHttpVersion,
  "com.typesafe.akka" %% "akka-http-spray-json" % akkaHttpVersion,
  // kryo serializer
  "io.altoo" %% "akka-kryo-serialization" % kryoVersion,
  // local levelDB stores
  "org.fusesource.leveldbjni" % "leveldbjni-all" % "1.8",
  // akka test kit
  "com.typesafe.akka" %% "akka-remote" % akkaVersion % Test,
  "com.typesafe.akka" %% "akka-testkit" % akkaVersion % Test,
  "com.typesafe.akka" %% "akka-stream-testkit" % akkaVersion % Test,
  "com.typesafe.akka" %% "akka-multi-node-testkit" % akkaVersion % Test,
  "com.typesafe.akka" %% "akka-actor-testkit-typed" % akkaVersion % Test,
  // akka http test kit
  "com.typesafe.akka" %% "akka-http-testkit" % akkaHttpVersion % Test,
  // test
  "org.scalatest" %% "scalatest" % "3.2.0" % Test,
  "org.scalamock" %% "scalamock" % "4.4.0" % Test
)

lazy val root = (project in file("."))
  .settings(
    dockerExposedPorts ++= Seq(2552, 8558, 8080),
    dockerExposedVolumes := Seq("/opt/docker/data")
  )
  .enablePlugins(MultiJvmPlugin, JavaServerAppPackaging)
  .configs(MultiJvm)
