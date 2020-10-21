package common

import akka.remote.testkit.MultiNodeConfig
import com.typesafe.config.ConfigFactory

object MultiNodeSampleConfig extends MultiNodeConfig {
  val node1 = role("node-1")
  val node2 = role("node-2")
  val node3 = role("node-3")

  commonConfig(
    ConfigFactory
      .parseString("""
        |akka {
        |  loglevel = "INFO"
        |  log-dead-letters-during-shutdown = off
        |  actor {
        |    serialize-messages = on
        |  }
        |  remote {
        |    artery {
        |      canonical.port = 0
        |    }
        |  }
        |  cluster {
        |    log-info = off
        |    metrics.enabled = off
        |  }
        |}
      """.stripMargin)
      .withFallback(ConfigFactory.load("test.conf"))
  )
}
