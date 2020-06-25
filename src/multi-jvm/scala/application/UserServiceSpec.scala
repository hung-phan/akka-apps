package application

import akka.remote.testkit.MultiNodeSpec
import akka.testkit.ImplicitSender
import common.{MultiNodeSampleConfig, STMultiNodeSpec}

class UserServiceSpec
    extends MultiNodeSpec(MultiNodeSampleConfig)
    with STMultiNodeSpec
    with ImplicitSender {

  import MultiNodeSampleConfig._

  def initialParticipants = roles.size

  "A MultiNodeSample" should {
    "wait for all nodes to enter a barrier" in {
      enterBarrier("startup")
    }

    "send to and receive from a remote node" in {
      runOn(node1) {
        enterBarrier("deployed")
//        val ponger = system.actorSelection(node(node2) / "user" / "ponger")
//        ponger ! "ping"
//        import scala.concurrent.duration._
//        expectMsg(10.seconds, "pong")
      }

      runOn(node2) {
//        system.actorOf(Props[Ponger](), "ponger")
//        enterBarrier("deployed")
      }

      enterBarrier("finished")
    }
  }
}

class UserServiceMultiJvm1 extends UserServiceSpec
class UserServiceMultiJvm2 extends UserServiceSpec
