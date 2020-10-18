package application

import akka.actor.typed._
import akka.actor.typed.scaladsl.AskPattern.Askable
import akka.actor.typed.scaladsl.{ActorContext, Behaviors}
import akka.cluster.sharding.typed.ShardingEnvelope
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
import akka.stream.scaladsl.{Flow, GraphDSL}
import akka.stream.typed.scaladsl.{ActorSink, ActorSource}
import akka.stream.{FlowShape, Materializer, OverflowStrategy}
import akka.util.{CompactByteString, Timeout}
import domain.common.ID
import domain.model.UserModel.UserConnections
import infrastructure.serializer.KryoSerializable

import scala.concurrent.duration._
import scala.concurrent.{ExecutionContext, Future}
import scala.language.postfixOps

object UserService {
  sealed trait UserCommand extends KryoSerializable
  case class AddSocket(socket: ActorRef[UserSocketCommand]) extends UserCommand
  case class RemoveSocket(socket: ActorRef[UserSocketCommand])
      extends UserCommand
  case class BroadcastMsg(msg: String) extends UserCommand
  case class ProcessMsg(msg: String, replyTo: ActorRef[UserSocketCommand])
      extends UserCommand
  case class QueryState(ref: ActorRef[QueryStateResp]) extends UserCommand
  case object ReceiveTimeout extends UserCommand
  case object Terminate extends UserCommand

  sealed trait UserCommandResp extends KryoSerializable
  case class QueryStateResp(sockets: Set[ActorRef[UserSocketCommand]])
      extends UserCommandResp

  sealed trait UserEvent extends KryoSerializable
  case class AddedConn(conn: ActorRef[UserSocketCommand]) extends UserEvent
  case class RemovedConn(conn: ActorRef[UserSocketCommand]) extends UserEvent

  sealed trait UserSocketCommand
  case class ProcessSocketMsg(msg: String) extends UserSocketCommand
  case class DispatchMsgToUser(msg: String) extends UserSocketCommand
  case object SocketDisconnect extends UserSocketCommand
  case class SocketFailure(ex: Throwable) extends UserSocketCommand

  val TypeKey = EntityTypeKey[UserCommand]("UserEntity")

  def fromEntityRef(
      entityId: String,
      sharding: ClusterSharding
  ): EntityRef[UserCommand] = sharding.entityRefFor(TypeKey, entityId)

  private def handleUserCommand(
      cmd: UserCommand,
      state: UserConnections[UserSocketCommand],
      ctx: ActorContext[UserCommand],
      shard: ActorRef[ClusterSharding.ShardCommand]
  ): Effect[UserEvent, UserConnections[UserSocketCommand]] =
    cmd match {
      case AddSocket(conn) =>
        if (state.sockets.contains(conn)) {
          Effect.none
        } else {
          Effect.persist(AddedConn(conn))
        }

      case RemoveSocket(conn) =>
        if (state.sockets.contains(conn)) {
          Effect.persist(RemovedConn(conn))
        } else {
          Effect.none
        }

      case QueryState(ref) =>
        ref ! QueryStateResp(state.sockets)
        Effect.none

      case BroadcastMsg(msg) =>
        state.sockets.foreach { _ ! DispatchMsgToUser(msg) }
        Effect.none

      case ProcessMsg(msg, _) =>
        state.sockets.foreach { _ ! DispatchMsgToUser(s"Reply from server: ${msg}") }
        Effect.none

      case ReceiveTimeout =>
        shard ! Passivate(ctx.self)
        Effect.none

      case Terminate =>
        Effect.stop
    }

  private def handleUserEvent(
      state: UserConnections[UserSocketCommand],
      event: UserEvent
  ): UserConnections[UserSocketCommand] =
    event match {
      case AddedConn(conn) =>
        state.copy(sockets = state.sockets + conn)

      case RemovedConn(conn) =>
        state.copy(sockets = state.sockets - conn)
    }

  def user(
      entityId: String,
      shard: ActorRef[ClusterSharding.ShardCommand]
  ): Behavior[UserCommand] =
    Behaviors.setup { ctx =>
      ctx.setReceiveTimeout(5 minutes, ReceiveTimeout)

      EventSourcedBehavior[UserCommand, UserEvent, UserConnections[
        UserSocketCommand
      ]](
        persistenceId = PersistenceId.ofUniqueId(entityId),
        emptyState = UserConnections(ID(entityId), Set.empty),
        commandHandler =
          (state, command) => handleUserCommand(command, state, ctx, shard),
        eventHandler = (state, event) => handleUserEvent(state, event)
      ).onPersistFailure(
          SupervisorStrategy.restartWithBackoff(1 second, 30 seconds, 0.2)
        )
        .withRetention(RetentionCriteria.snapshotEvery(20, 1))
    }

  def userSocket(
      entityId: String,
      downstream: ActorRef[UserSocketCommand],
      userShardRegion: ActorRef[ShardingEnvelope[UserCommand]]
  )(implicit materializer: Materializer): Behavior[UserSocketCommand] = {
    Behaviors.setup { ctx =>
      userShardRegion ! ShardingEnvelope(entityId, AddSocket(ctx.self))

      Behaviors
        .receiveMessagePartial[UserSocketCommand] {
          case ProcessSocketMsg(msg) =>
            userShardRegion ! ShardingEnvelope(
              entityId,
              ProcessMsg(msg, ctx.self)
            )
            Behaviors.same

          case DispatchMsgToUser(msg) =>
            downstream ! DispatchMsgToUser(msg)
            Behaviors.same

          case SocketDisconnect =>
            Behaviors.stopped

          case SocketFailure(ex) =>
            throw ex
        }
        .receiveSignal {
          case (ctx, PostStop) =>
            userShardRegion ! ShardingEnvelope(entityId, RemoveSocket(ctx.self))
            Behaviors.ignore
        }
    }
  }

  val wsDeserializerFlow: Flow[Message, UserSocketCommand, _] = Flow[Message]
    .collect[UserSocketCommand] {
      case TextMessage.Strict(txt)    => ProcessSocketMsg(txt)
      case BinaryMessage.Strict(data) => ProcessSocketMsg(data.utf8String)
    }

  private val wsTextSerializerFlow = Flow[UserSocketCommand]
    .collect[Message] {
      case DispatchMsgToUser(msg) => TextMessage.Strict(msg)
    }

  private val wsBinarySerializerFlow = Flow[UserSocketCommand]
    .collect[Message] {
      case DispatchMsgToUser(msg) =>
        BinaryMessage(CompactByteString(msg.toByte))
    }

  def createWsFlow(
      protocol: String,
      userId: String,
      mainSystem: ActorRef[MainSystem.Command]
  )(implicit
      system: ActorSystem[_],
      ec: ExecutionContext,
      scheduler: Scheduler
  ): Future[Flow[Message, Message, _]] = {
    implicit val timeout = Timeout(5 seconds)

    val (socket, socketSource) = ActorSource
      .actorRef[UserSocketCommand](
        completionMatcher = { case SocketDisconnect => },
        failureMatcher = { case SocketFailure(ex) => throw ex },
        bufferSize = 16,
        OverflowStrategy.fail
      )
      .preMaterialize()

    val connPromise = mainSystem ? (replyTo =>
      MainSystem.CreateUserSocket(userId, socket, replyTo)
    )

    connPromise.map {
      case MainSystem.CreateUserSocketResp(conn) =>
        Flow.fromGraph(
          GraphDSL.create(socketSource) { implicit builder => socket =>
            import GraphDSL.Implicits._

            // transforms messages from the websockets into the actor's protocol
            val webSocketSource = builder.add(UserService.wsDeserializerFlow)

            // transform a message from the WebSocketProtocol back into a websocket text message
            val webSocketSink = builder.add(protocol match {
              case "text"   => wsTextSerializerFlow
              case "binary" => wsBinarySerializerFlow
            })

            // route messages to the session actor
            val forwardMsgToConn = builder.add(
              ActorSink.actorRef[UserService.UserSocketCommand](
                ref = conn,
                onCompleteMessage = UserService.SocketDisconnect,
                onFailureMessage = UserService.SocketFailure.apply
              )
            )

            // ~> connects everything
            webSocketSource ~> forwardMsgToConn
            socket ~> webSocketSink

            FlowShape(webSocketSource.in, webSocketSink.out)
          }
        )
    }
  }
}
