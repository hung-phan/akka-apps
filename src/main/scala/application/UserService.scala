package application

import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.{ActorRef, Behavior}
import akka.cluster.sharding.typed.scaladsl.{
  ClusterSharding,
  EntityRef,
  EntityTypeKey
}
import domain.model.Connection.{
  Connection,
  ConnectionManager,
  Command => ConnectionCommand
}

object UserService {
  sealed trait Command
  case class AddConnection(conn: Connection) extends Command
  case class RemoveConnection(conn: Connection) extends Command
  case class DispatchCmd(connectionCmd: ConnectionCommand) extends Command

  val TypeKey = EntityTypeKey[Command]("UserEntity")

  def Actor(entityID: String,
            shard: ActorRef[ClusterSharding.ShardCommand],
            connectionManager: ConnectionManager): Behavior[Command] =
    Behaviors.setup { ctx =>
      Behaviors.receiveMessagePartial {
        case AddConnection(conn) =>
          Actor(entityID, shard, connectionManager :+ conn)

        case RemoveConnection(conn) =>
          Actor(entityID, shard, connectionManager :- conn)

        case DispatchCmd(cmd) =>
          connectionManager.dispatch(cmd)
          Behaviors.same
      }
    }

  def getEntityRef(sharding: ClusterSharding,
                   entityID: String): EntityRef[Command] =
    sharding.entityRefFor(TypeKey, entityID)
}
