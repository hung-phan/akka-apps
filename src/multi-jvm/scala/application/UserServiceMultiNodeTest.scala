package application

import akka.actor.testkit.typed.scaladsl.TestProbe
import akka.actor.typed.ActorRef
import akka.cluster.ClusterEvent.{ClusterDomainEvent, MemberUp}
import akka.cluster.sharding.typed.scaladsl.{ClusterSharding, Entity}
import akka.cluster.sharding.typed.{ClusterShardingSettings, ShardingEnvelope}
import akka.cluster.typed.{Join, Subscribe}
import akka.remote.testconductor.RoleName
import akka.remote.testkit.MultiNodeSpec
import akka.testkit.ImplicitSender
import application.ConnectionService.SendClientMsg
import application.UserService.{AddConn, Broadcast}
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

  def initialParticipants = roles.size

  def join(from: RoleName, to: RoleName): Unit = {
    runOn(from) {
      cluster.manager ! Join(node(to).address)
    }

    enterBarrier(s"${from.name}-joined")
  }

  def startProxySharding(): ActorRef[ShardingEnvelope[UserService.Command]] = {
    ClusterSharding(typedSystem).init(
      Entity(UserService.TypeKey)(entityContext =>
        UserService(
          entityContext.entityId,
          entityContext.shard,
          List.empty
        )
      ).withSettings(ClusterShardingSettings(typedSystem))
    )
  }

  "UserServiceSpec" should {
    var regionActorOption
        : Option[ActorRef[ShardingEnvelope[UserService.Command]]] = None

    "join cluster" in within(15 seconds) {
      join(node1, node1)
      join(node2, node1)
      join(node3, node1)

      // test cluster status
      val probe = TestProbe[ClusterDomainEvent]()

      cluster.subscriptions ! Subscribe(probe.ref, classOf[MemberUp])
      probe.expectMessageType[MemberUp]

      // start proxy sharding
      regionActorOption = Some(startProxySharding())

      enterBarrier("bootstrapped")
    }

    val userId = "user-1"
    val serializedData = "[1, 2, 3]"
    var connectionProbe: Option[TestProbe[ConnectionService.Command]] = None

    "be able to relay message to all available connections" in within(
      15 seconds
    ) {
      runOn(node1, node2) {
        connectionProbe = Some(TestProbe[ConnectionService.Command]())

        for {
          region <- regionActorOption
          connectionProbe <- connectionProbe
        } {
          region ! ShardingEnvelope(
            userId,
            AddConn(connectionProbe.ref)
          )
        }
      }

      runOn(node3) {
        regionActorOption.map { regionActor =>
          regionActor ! ShardingEnvelope(
            userId,
            Broadcast(serializedData)
          )
        }
      }

      runOn(node1, node2) {
        awaitAssert {
          connectionProbe.map { probe =>
            probe.expectMessage(SendClientMsg(serializedData))
          }
        }
      }
    }
  }
}
