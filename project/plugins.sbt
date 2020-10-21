// Comment to get more information during initialization
logLevel := Level.Warn

// Resolvers
resolvers += "spray repo" at "https://repo.spray.io"

resolvers += "Typesafe repository" at "https://repo.typesafe.com/typesafe/releases/"

addSbtPlugin("com.timushev.sbt" % "sbt-updates"         % "0.5.0")
addSbtPlugin("com.typesafe.sbt" % "sbt-multi-jvm"       % "0.4.0")
addSbtPlugin("com.typesafe.sbt" % "sbt-native-packager" % "1.7.6")
