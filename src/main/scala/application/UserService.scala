package application

import akka.actor.typed.scaladsl.{ActorContext, Behaviors}
import akka.actor.typed.{ActorRef, Behavior, PostStop, SupervisorStrategy}
import akka.cluster.sharding.typed.scaladsl.ClusterSharding.Passivate
import akka.cluster.sharding.typed.scaladsl.{
  ClusterSharding,
  EntityRef,
  EntityTypeKey
}
import akka.http.scaladsl.model.ws.{BinaryMessage, Message, TextMessage}
import akka.persistence.typed.PersistenceId
import akka.persistence.typed.scaladsl.{
  Effect,
  EventSourcedBehavior,
  RetentionCriteria
}
import akka.stream.Materializer
import akka.stream.scaladsl.{Sink, Source}
import akka.util.CompactByteString
import domain.common.ID
import domain.model.UserModel.UserConnections
import infrastructure.serializer.KryoSerializable

import scala.concurrent.duration._
import scala.language.postfixOps

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
  case class RemoveConn(conn: ActorRef[ConnectionCommand]) extends UserCommand
  case class Broadcast(msg: String) extends UserCommand
  case class Process(msg: String, replyTo: ActorRef[ConnectionCommand])
      extends UserCommand
  case class QueryState(ref: ActorRef[CurrentState]) extends UserCommand
  case object ReceiveTimeout extends UserCommand
  case object Terminate extends UserCommand

  sealed trait UserEvent extends KryoSerializable
  case class AddedConn(conn: ActorRef[ConnectionCommand]) extends UserEvent
  case class RemovedConn(conn: ActorRef[ConnectionCommand]) extends UserEvent

  val TypeKey = EntityTypeKey[UserCommand]("UserEntity")

  def fromEntityRef(
      entityId: String,
      sharding: ClusterSharding
  ): EntityRef[UserCommand] = sharding.entityRefFor(TypeKey, entityId)

  private def handleUserCommand(
      cmd: UserCommand,
      state: UserConnections[ConnectionCommand],
      ctx: ActorContext[UserCommand],
      shard: ActorRef[ClusterSharding.ShardCommand]
  ): Effect[UserEvent, UserConnections[ConnectionCommand]] =
    cmd match {
      case AddConn(conn) =>
        state.conns.find(conn == _) match {
          case Some(_) => Effect.none
          case None    => Effect.persist(AddedConn(conn))
        }

      case RemoveConn(conn) =>
        state.conns.find(conn == _) match {
          case Some(_) => Effect.persist(RemovedConn(conn))
          case None    => Effect.none
        }

      case QueryState(ref) =>
        ref ! CurrentState(state.conns)
        Effect.none

      case Broadcast(msg) =>
        state.conns.foreach { _ ! SendClientMsg(msg) }
        Effect.none

      case Process(msg, _) =>
        ctx.log.info(msg)
        Effect.none

      case ReceiveTimeout =>
        shard ! Passivate(ctx.self)
        Effect.none

      case Terminate =>
        Effect.stop
    }

  private def handleUserEvent(
      state: UserConnections[ConnectionCommand],
      event: UserEvent
  ): UserConnections[ConnectionCommand] =
    event match {
      case AddedConn(conn) =>
        state.copy(conns = conn :: state.conns)

      case RemovedConn(conn) =>
        state.copy(conns = state.conns.filter(conn != _))
    }

  def user(
      entityId: String,
      shard: ActorRef[ClusterSharding.ShardCommand]
  ): Behavior[UserCommand] =
    Behaviors.setup { ctx =>
      ctx.setReceiveTimeout(5 minutes, ReceiveTimeout)

      EventSourcedBehavior[UserCommand, UserEvent, UserConnections[
        ConnectionCommand
      ]](
        persistenceId = PersistenceId.ofUniqueId(entityId),
        emptyState = UserConnections(ID(entityId), List.empty),
        commandHandler =
          (state, command) => handleUserCommand(command, state, ctx, shard),
        eventHandler = (state, event) => handleUserEvent(state, event)
      ).onPersistFailure(
          SupervisorStrategy.restartWithBackoff(1 second, 30 seconds, 0.2)
        )
        .withRetention(RetentionCriteria.snapshotEvery(20, 1))
    }

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

  def userConnection(
      entityId: String,
      protocol: ConnectionProtocol,
      outputSink: Sink[Message, _]
  )(implicit
      sharding: ClusterSharding,
      materializer: Materializer
  ): Behavior[ConnectionCommand] = {
    Behaviors.setup { ctx =>
      val user = UserService.fromEntityRef(entityId, sharding)

      user ! AddConn(ctx.self)

      Behaviors
        .receiveMessagePartial[ConnectionCommand] {
          case HandleClientMsg(msg) =>
            user ! Process(msgDeserializer(msg), ctx.self)
            Behaviors.same

          case SendClientMsg(msg) =>
            Source
              .single(msg)
              .map { msgSerializer(protocol, _) }
              .runWith(outputSink)
            Behaviors.same

          case Stop =>
            Behaviors.stopped
        }
        .receiveSignal {
          case (ctx, PostStop) =>
            user ! RemoveConn(ctx.self)
            Behaviors.ignore
        }
    }
  }
}
