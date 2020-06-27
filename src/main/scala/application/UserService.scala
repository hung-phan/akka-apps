package application

import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.{ActorRef, Behavior}
import akka.cluster.sharding.typed.scaladsl.{ClusterSharding, EntityRef, EntityTypeKey}
import domain.common.SerializableMsg
import domain.model.Connection.{Connection, ConnectionManager, Command => ConnectionCommand}

object UserService {
  sealed trait Command extends SerializableMsg
  case class AddConnection(conn: Connection) extends Command
  case class RemoveConnection(conn: Connection) extends Command
  case class DispatchCmd(connectionCmd: ConnectionCommand) extends Command
  case object Print extends Command

  val TypeKey = EntityTypeKey[Command]("UserEntity")

  def apply(entityID: String,
            shard: ActorRef[ClusterSharding.ShardCommand],
            connectionManager: ConnectionManager): Behavior[Command] =
    Behaviors.setup { ctx =>
      Behaviors.receiveMessagePartial {
        case AddConnection(conn) =>
          UserService(entityID, shard, connectionManager :+ conn)

        case RemoveConnection(conn) =>
          UserService(entityID, shard, connectionManager :- conn)

        case DispatchCmd(cmd) =>
          connectionManager.dispatch(cmd)
          Behaviors.same

        case Print =>
          ctx.log.info(s"Receive entityID: $entityID")
          Behaviors.same
      }
    }

  def getEntityRef(sharding: ClusterSharding,
                   entityID: String): EntityRef[Command] =
    sharding.entityRefFor(TypeKey, entityID)
}
