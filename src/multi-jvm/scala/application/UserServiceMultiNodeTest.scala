package application

import akka.actor.testkit.typed.scaladsl.TestProbe
import akka.actor.typed.ActorRef
import akka.cluster.ClusterEvent.{ClusterDomainEvent, MemberUp}
import akka.cluster.sharding.typed.ShardingEnvelope
import akka.cluster.typed.Subscribe
import akka.http.scaladsl.model.ws.{Message, TextMessage}
import akka.remote.testkit.MultiNodeSpec
import akka.stream.scaladsl.Sink
import akka.testkit.ImplicitSender
import common.{MultiNodeSampleConfig, STMultiNodeSpec}

import scala.concurrent.duration._
import scala.language.postfixOps

class UserServiceMultiJvm1 extends UserServiceMultiNodeTest
class UserServiceMultiJvm2 extends UserServiceMultiNodeTest
class UserServiceMultiJvm3 extends UserServiceMultiNodeTest

class UserServiceMultiNodeTest
    extends MultiNodeSpec(MultiNodeSampleConfig)
    with STMultiNodeSpec
    with ImplicitSender {

  import MultiNodeSampleConfig._

  "UserServiceSpec" should {
    var regionOpt: Option[ActorRef[ShardingEnvelope[UserService.UserCommand]]] =
      None

    "join cluster" in within(15 seconds) {
      join(node1, node1)
      join(node2, node1)
      join(node3, node1)

      // test cluster status
      val probe = TestProbe[ClusterDomainEvent]()

      cluster.subscriptions ! Subscribe(probe.ref, classOf[MemberUp])
      probe.expectMessageType[MemberUp]

      // start proxy sharding
      regionOpt = Some(
        startProxySharding(
          UserService.TypeKey,
          entityContext =>
            UserService.user(entityContext.entityId, entityContext.shard)
        )
      )

      enterBarrier("bootstrap")
    }

    val userId = "user-1"

    "be able to register connection and receive message from user actor" in within(
      15 seconds
    ) {
      val serializedData = "[1, 2, 3]"
      val user = UserService.fromEntityRef(userId, sharding)

      runOn(node1, node2) {
        val sinkProbe = TestProbe[Message]()

        testKit.spawn(
          UserService.userConnection(
            userId,
            UserService.TextProtocol,
            Sink.foreach[Message](sinkProbe.ref ! _)
          )
        )

        val userProbe = TestProbe[UserService.CurrentState]

        awaitAssert(
          {
            user ! UserService.QueryState(userProbe.ref)

            assert(userProbe.receiveMessage().conns.size == 2)
            sinkProbe.expectMessage(TextMessage.Strict(serializedData))
          },
          interval = 1 second
        )
      }

      runOn(node3) {
        user ! UserService.Broadcast(serializedData)
      }
    }
  }
}
