package application

import akka.actor.typed.javadsl.ActorContext
import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.{Behavior, PostStop}
import akka.cluster.sharding.typed.scaladsl.{ClusterSharding, EntityRef}
import akka.http.scaladsl.model.ws.{BinaryMessage, Message, TextMessage}
import akka.stream.Materializer
import akka.stream.scaladsl.{Flow, MergeHub, Sink, Source}
import akka.util.CompactByteString
import domain.model.UserModel.UserEntity
import infrastructure.serializer.KryoSerializable

object ConnectionService {
  sealed trait Command extends KryoSerializable
  case class HandleClientMsg(msg: Message) extends Command
  case class SendClientMsg(msg: String) extends Command
  case object Stop extends Command

  sealed trait Protocol extends KryoSerializable
  case object TextProtocol extends Protocol
  case object BinaryProtocol extends Protocol

  private def serializeMsg(protocol: Protocol, msg: String): Message =
    protocol match {
      case TextProtocol =>
        TextMessage.Strict(msg)
      case BinaryProtocol =>
        BinaryMessage(CompactByteString(msg.toByte))
    }

  private val deserializeTextMsg: PartialFunction[Message, String] = {
    case tm: TextMessage.Strict => tm.text
  }

  private val deserializerBinaryMsg: PartialFunction[Message, String] = {
    case bm: BinaryMessage.Strict => bm.data.utf8String
  }

  def createWebSocketFlow(
      protocol: Protocol,
      ctx: ActorContext[_],
      userEntity: UserEntity,
      sharding: ClusterSharding
  )(implicit
      materializer: Materializer
  ): Flow[Message, Message, Any] = {
    val source = MergeHub.source[Message]
    val connActor = ctx.spawnAnonymous(
      ConnectionService(
        protocol,
        UserService.fromEntityRef(userEntity.id.value, sharding),
        source.to(Sink.ignore).run()
      )
    )

    Flow
      .fromSinkAndSource(
        Sink
          .foreach[Message](
            connActor ! ConnectionService.HandleClientMsg(_)
          ),
        source
      )
  }

  def apply(
      protocol: Protocol,
      userActor: EntityRef[UserService.Command],
      materializedSink: Sink[Message, _]
  )(implicit materializer: Materializer): Behavior[Command] = {
    Behaviors.setup { ctx =>
      userActor ! UserService.AddConn(ctx.self)

      Behaviors
        .receiveMessagePartial[Command] {
          case HandleClientMsg(msg) =>
            userActor ! UserService.Process(
              (deserializeTextMsg orElse deserializerBinaryMsg)(msg),
              ctx.self
            )
            Behaviors.same

          case SendClientMsg(msg) =>
            Source
              .single(msg)
              .map { serializeMsg(protocol, _) }
              .runWith(materializedSink)
            Behaviors.same

          case Stop =>
            Behaviors.stopped
        }
        .receiveSignal {
          case (ctx, PostStop) =>
            userActor ! UserService.DeleteConn(ctx.self)
            Behaviors.ignore
        }
    }
  }
}
