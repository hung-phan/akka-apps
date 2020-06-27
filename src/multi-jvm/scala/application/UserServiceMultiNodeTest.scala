package application

import akka.actor.typed.ActorRef
import akka.cluster.sharding.typed.scaladsl.{ClusterSharding, Entity}
import akka.cluster.sharding.typed.{ClusterShardingSettings, ShardingEnvelope}
import akka.cluster.typed.Join
import akka.remote.testconductor.RoleName
import akka.remote.testkit.MultiNodeSpec
import akka.testkit.ImplicitSender
import application.ConnectionService.ConnectionManager
import application.UserService.Print
import common.{MultiNodeSampleConfig, STMultiNodeSpec}
import org.scalamock.scalatest.MockFactory

import scala.concurrent.duration._
import scala.language.postfixOps

class UserServiceMultiJvm1 extends UserServiceMultiNodeTest
class UserServiceMultiJvm2 extends UserServiceMultiNodeTest
class UserServiceMultiJvm3 extends UserServiceMultiNodeTest

class UserServiceMultiNodeTest
    extends MultiNodeSpec(MultiNodeSampleConfig)
    with STMultiNodeSpec
    with ImplicitSender
    with MockFactory {

  import MultiNodeSampleConfig._

  def initialParticipants = roles.size

  def join(from: RoleName, to: RoleName): Unit = {
    runOn(from) {
      cluster.manager ! Join(node(to).address)

      startPersistentSharding()
    }

    enterBarrier(from.name + "-joined")
  }

  def startPersistentSharding()
    : ActorRef[ShardingEnvelope[UserService.Command]] = {
    ClusterSharding(typedSystem).init(
      Entity(UserService.TypeKey)(
        entityContext =>
          UserService(
            entityContext.entityId,
            entityContext.shard,
            ConnectionManager(Set.empty)
        )
      ).withSettings(ClusterShardingSettings(typedSystem).withRole("node1"))
    )
  }

  def startProxySharding(): ActorRef[ShardingEnvelope[UserService.Command]] = {
    ClusterSharding(typedSystem).init(
      Entity(UserService.TypeKey)(
        entityContext =>
          UserService(
            entityContext.entityId,
            entityContext.shard,
            ConnectionManager(Set.empty)
        )
      )
    )
  }

  "UserServiceSpec" should {
    "should join cluster" in within(15 seconds) {
      join(node1, node1)
      join(node2, node1)
      join(node3, node1)

      enterBarrier("all-up")
    }

    "should be able to send message to other node" in within(15 seconds) {
      runOn(node1, node2, node3) {
        val region = startProxySharding()
        region ! ShardingEnvelope("1", Print)
      }
      enterBarrier("after sending message to user actor")
    }
  }
}
