package common

import akka.remote.testkit.MultiNodeConfig
import com.typesafe.config.ConfigFactory

object MultiNodeSampleConfig extends MultiNodeConfig {
  val node1 = role("node-1")
  val node2 = role("node-2")
  val node3 = role("node-3")

  commonConfig(
    ConfigFactory.parseString("""
      |akka.loglevel = INFO
      |akka.cluster.roles = [compute]
      |akka.remote.artery.canonical.port = 0
      |akka.log-dead-letters-during-shutdown = off
    """.stripMargin).withFallback(ConfigFactory.load("application.conf"))
  )
}
