package application

import akka.actor.typed.scaladsl.{ActorContext, Behaviors}
import akka.actor.typed.{ActorRef, Behavior, SupervisorStrategy}
import akka.cluster.sharding.typed.scaladsl.ClusterSharding.Passivate
import akka.cluster.sharding.typed.scaladsl.{
  ClusterSharding,
  EntityRef,
  EntityTypeKey
}
import akka.persistence.typed.PersistenceId
import akka.persistence.typed.scaladsl.{
  Effect,
  EventSourcedBehavior,
  RetentionCriteria
}
import domain.model.Chat.{ChatLogEntity, ChatState}
import domain.model.User.UserEntity
import domain.serializer.MsgSerializeMarker

import scala.concurrent.duration._
import scala.language.postfixOps

object ChatService {
  sealed trait Command extends MsgSerializeMarker
  case class AddUser(user: UserEntity) extends Command
  case class RemoveUser(user: UserEntity) extends Command
  case class AppendMsg(msg: ChatLogEntity) extends Command
  case object ReceiveTimeout extends Command
  case object Terminate extends Command

  sealed trait Event
  case class AddedUser(user: UserEntity) extends Event
  case class RemovedUser(user: UserEntity) extends Event
  case class AppendedMsg(smg: ChatLogEntity) extends Event

  val TypeKey = EntityTypeKey[Command]("ChatEntity")

  private def onCommand(shard: ActorRef[ClusterSharding.ShardCommand],
                        ctx: ActorContext[Command],
                        command: Command): Effect[Event, ChatState] = {
    command match {
      case AddUser(user) =>
        Effect.persist(AddedUser(user))

      case RemoveUser(user) =>
        Effect.persist(RemovedUser(user))

      case AppendMsg(msg) =>
        Effect.persist(AppendedMsg(msg))

      case ReceiveTimeout =>
        shard ! Passivate(ctx.self)
        Effect.none

      case Terminate =>
        Effect.stop()
    }
  }

  private def applyEvent(state: ChatState, event: Event): ChatState = {
    event match {
      case AddedUser(user) =>
        state.addUser(user)

      case RemovedUser(user) =>
        state.removeUser(user)

      case AppendedMsg(msg) =>
        state.appendMsg(msg)
    }
  }

  def apply(entityID: String,
            shard: ActorRef[ClusterSharding.ShardCommand]): Behavior[Command] =
    Behaviors.setup { ctx =>
      ctx.setReceiveTimeout(5 minutes, ReceiveTimeout)

      EventSourcedBehavior[Command, Event, ChatState](
        persistenceId = PersistenceId.ofUniqueId(entityID),
        emptyState = ChatState(entityID, Set.empty, List.empty, List.empty),
        commandHandler = (_, command) => onCommand(shard, ctx, command),
        eventHandler = (state, event) => applyEvent(state, event)
      ).onPersistFailure(
          SupervisorStrategy.restartWithBackoff(1 second, 30 seconds, 0.2)
        )
        .withRetention(RetentionCriteria.snapshotEvery(10, 2))
    }

  def getEntityRef(sharding: ClusterSharding,
                   entityID: String): EntityRef[Command] =
    sharding.entityRefFor(TypeKey, entityID)
}
