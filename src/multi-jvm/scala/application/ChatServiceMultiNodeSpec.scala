package application

import akka.actor.testkit.typed.scaladsl.TestProbe
import akka.actor.typed.ActorRef
import akka.cluster.ClusterEvent.{ClusterDomainEvent, MemberUp}
import akka.cluster.sharding.typed.ShardingEnvelope
import akka.cluster.typed.Subscribe
import akka.remote.testkit.MultiNodeSpec
import akka.testkit.ImplicitSender
import application.ChatService.{AddUser, AppendMsg, QueryStateResp, QueryState}
import common.{MultiNodeSampleConfig, STMultiNodeSpec}
import domain.common.{ID, MsgType}
import domain.model.ChatModel.{ChatState, TextChatLog}
import domain.model.UserModel.UserIdentifier

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

  "ChatServiceSpec" should {
    var regionOpt: Option[ActorRef[ShardingEnvelope[ChatService.Command]]] =
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
          ChatService.TypeKey,
          entityContext =>
            ChatService(entityContext.entityId, entityContext.shard)
        )
      )

      enterBarrier("bootstrap")
    }

    val chatId = "chat-1"

    "be able to update chat state" in within(15 seconds) {
      val user1 = UserIdentifier(ID("user-1"))
      val user2 = UserIdentifier(ID("user-2"))

      runOn(node1) {
        regionOpt.map { region =>
          region ! ShardingEnvelope(chatId, AddUser(user1))
          region ! ShardingEnvelope(chatId, AddUser(user2))
        }
      }

      runOn(node2) {
        regionOpt.map { region =>
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
        regionOpt.map { region =>
          awaitAssert(
            {
              val probe = TestProbe[QueryStateResp]()

              region ! ShardingEnvelope(
                chatId,
                QueryState(probe.ref)
              )

              probe.expectMessage(
                QueryStateResp(
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
              )
            },
            interval = 1 second
          )
        }
      }
    }
  }
}
