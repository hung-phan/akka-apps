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
import application.ChatService.{AddUser, AppendMsg, QueryState}
import common.{MultiNodeSampleConfig, STMultiNodeSpec}
import domain.common.{ID, MsgType}
import domain.model.ChatModel.{ChatState, ChatStateEntity, TextChatLog}
import domain.model.UserModel.UserWithIdentityOnly

import scala.concurrent.duration._
import scala.language.postfixOps

class ChatServiceMultiJvm1 extends ChatServiceMultiNodeTest
class ChatServiceMultiJvm2 extends ChatServiceMultiNodeTest
class ChatServiceMultiJvm3 extends ChatServiceMultiNodeTest

class ChatServiceMultiNodeTest
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

  def startProxySharding(): ActorRef[ShardingEnvelope[ChatService.Command]] = {
    ClusterSharding(typedSystem).init(
      Entity(ChatService.TypeKey)(entityContext =>
        ChatService(
          entityContext.entityId,
          entityContext.shard
        )
      ).withSettings(ClusterShardingSettings(typedSystem))
    )
  }

  "ChatServiceSpec" should {
    var regionActorOption
        : Option[ActorRef[ShardingEnvelope[ChatService.Command]]] = None

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

    val chatId = "chat-1"
    val user1 = UserWithIdentityOnly(ID("user-1"))
    val user2 = UserWithIdentityOnly(ID("user-2"))

    "be able to update chat state" in within(15 seconds) {
      runOn(node1) {
        regionActorOption.map { region =>
          region ! ShardingEnvelope(chatId, AddUser(user1))
          region ! ShardingEnvelope(chatId, AddUser(user2))
        }
      }

      runOn(node2) {
        regionActorOption.map { region =>
          region ! ShardingEnvelope(
            chatId,
            AppendMsg(TextChatLog(MsgType("Hello from the other side"), user1))
          )
          region ! ShardingEnvelope(
            chatId,
            AppendMsg(TextChatLog(MsgType("Hello there"), user2))
          )
        }
      }

      runOn(node3) {
        regionActorOption.map { region =>
          awaitAssert {
            val probe = TestProbe[ChatStateEntity]()

            region ! ShardingEnvelope(
              chatId,
              QueryState(probe.ref)
            )

            probe.expectMessage(
              ChatState(
                ID("chat-1"),
                Set(user1, user2),
                List.empty,
                List(
                  TextChatLog(MsgType("Hello from the other side"), user1),
                  TextChatLog(MsgType("Hello there"), user2)
                )
              )
            )
          }
        }
      }
    }
  }
}
