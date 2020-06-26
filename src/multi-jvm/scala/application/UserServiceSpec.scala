package application

import akka.remote.testkit.MultiNodeSpec
import akka.testkit.ImplicitSender
import common.{MultiNodeSampleConfig, STMultiNodeSpec}

import scala.concurrent.duration._
import scala.language.postfixOps

class UserServiceSpec
    extends MultiNodeSpec(MultiNodeSampleConfig)
    with STMultiNodeSpec
    with ImplicitSender {

  import MultiNodeSampleConfig._

  def initialParticipants = roles.size

  "UserServiceSpec" should {
    "should join cluster" in within(15 seconds) {
      join(node1, node1)
      join(node2, node1)
      join(node3, node1)

      enterBarrier("after-1")
    }
  }
}

class UserServiceMultiJvm1 extends UserServiceSpec
class UserServiceMultiJvm2 extends UserServiceSpec
class UserServiceMultiJvm3 extends UserServiceSpec
