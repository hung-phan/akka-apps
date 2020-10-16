package application

import akka.actor.typed.javadsl.ActorContext
import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.{ActorRef, Behavior, PostStop}
import akka.cluster.sharding.typed.scaladsl.{
  ClusterSharding,
  EntityRef,
  EntityTypeKey
}
import akka.http.scaladsl.model.ws.{BinaryMessage, Message, TextMessage}
import akka.stream.Materializer
import akka.stream.scaladsl.{Flow, MergeHub, Sink, Source}
import akka.util.CompactByteString
import domain.model.UserModel.UserEntity
import infrastructure.serializer.KryoSerializable

object UserService {
  case class CurrentState(conns: List[ActorRef[ConnectionCommand]])

  sealed trait ConnectionProtocol extends KryoSerializable
  case object TextProtocol extends ConnectionProtocol
  case object BinaryProtocol extends ConnectionProtocol

  sealed trait ConnectionCommand extends KryoSerializable
  case class HandleClientMsg(msg: Message) extends ConnectionCommand
  case class SendClientMsg(msg: String) extends ConnectionCommand
  case object Stop extends ConnectionCommand

  sealed trait UserCommand extends KryoSerializable
  case class AddConn(conn: ActorRef[ConnectionCommand]) extends UserCommand
  case class DeleteConn(conn: ActorRef[ConnectionCommand]) extends UserCommand
  case class Broadcast(msg: String) extends UserCommand
  case class Process(msg: String, replyTo: ActorRef[ConnectionCommand])
      extends UserCommand
  case class QueryState(ref: ActorRef[CurrentState]) extends UserCommand

  val TypeKey = EntityTypeKey[UserCommand]("UserEntity")

  def fromEntityRef(
      entityID: String,
      sharding: ClusterSharding
  ): EntityRef[UserCommand] = sharding.entityRefFor(TypeKey, entityID)

  private val msgDeserializer: PartialFunction[Message, String] = {
    case tm: TextMessage.Strict   => tm.text
    case bm: BinaryMessage.Strict => bm.data.utf8String
  }

  private def msgSerializer(
      protocol: ConnectionProtocol,
      msg: String
  ): Message =
    protocol match {
      case TextProtocol =>
        TextMessage.Strict(msg)
      case BinaryProtocol =>
        BinaryMessage(CompactByteString(msg.toByte))
    }

  def getProtocol(protocol: String): ConnectionProtocol =
    protocol match {
      case "text"   => TextProtocol
      case "binary" => BinaryProtocol
    }

  def User(
      entityID: String,
      shard: ActorRef[ClusterSharding.ShardCommand],
      conns: List[ActorRef[ConnectionCommand]]
  ): Behavior[UserCommand] =
    Behaviors.setup { ctx =>
      Behaviors
        .receiveMessagePartial {
          case AddConn(conn) =>
            UserService.User(
              entityID,
              shard,
              conns.find(conn == _) match {
                case Some(_) => conns
                case None    => conn :: conns
              }
            )

          case DeleteConn(conn) =>
            UserService.User(entityID, shard, conns.filter { conn != _ })

          case QueryState(ref) =>
            ref ! CurrentState(conns)
            Behaviors.same

          case Broadcast(msg) =>
            conns.foreach { _ ! SendClientMsg(msg) }
            Behaviors.same

          case Process(msg, _) =>
            ctx.log.info(msg)
            Behaviors.same
        }
    }

  def UserConnection(
      protocol: ConnectionProtocol,
      user: EntityRef[UserService.UserCommand],
      materializedSink: Sink[Message, _]
  )(implicit materializer: Materializer): Behavior[ConnectionCommand] = {
    Behaviors.setup { ctx =>
      user ! AddConn(ctx.self)

      Behaviors
        .receiveMessagePartial[ConnectionCommand] {
          case HandleClientMsg(msg) =>
            user ! Process(
              msgDeserializer(msg),
              ctx.self
            )
            Behaviors.same

          case SendClientMsg(msg) =>
            Source
              .single(msg)
              .map { msgSerializer(protocol, _) }
              .runWith(materializedSink)
            Behaviors.same

          case Stop =>
            Behaviors.stopped
        }
        .receiveSignal {
          case (ctx, PostStop) =>
            user ! DeleteConn(ctx.self)
            Behaviors.ignore
        }
    }
  }

  def createWebSocketFlow(
      protocol: ConnectionProtocol,
      ctx: ActorContext[_],
      userEntity: UserEntity,
      sharding: ClusterSharding
  )(implicit
      materializer: Materializer
  ): Flow[Message, Message, Any] = {
    val source = MergeHub.source[Message]
    val conn = ctx.spawnAnonymous(
      UserConnection(
        protocol,
        UserService.fromEntityRef(userEntity.id.value, sharding),
        source.to(Sink.ignore).run()
      )
    )

    Flow.fromSinkAndSource(
      Sink.foreach[Message](conn ! HandleClientMsg(_)),
      source
    )
  }
}
