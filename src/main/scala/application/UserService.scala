package application

import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.{ActorRef, Behavior}
import akka.cluster.sharding.typed.scaladsl.{ClusterSharding, EntityRef, EntityTypeKey}
import infrastructure.common.KryoSerializer

object UserService {
  def apply(
             entityID: String,
             shard: ActorRef[ClusterSharding.ShardCommand],
             conns: List[ActorRef[ConnectionService.Command]]
           ): Behavior[Command] =
    Behaviors.setup { ctx =>
      Behaviors.receiveMessagePartial {
        case AddConnection(conn) =>
          UserService(
            entityID,
            shard,
            conns.find(_.path == conn.path) match {
              case Some(_) => conns
              case None => conn :: conns
            }
          )

        case RemoveConnection(conn) =>
          UserService(entityID, shard, conns.filter(_.path != conn.path))

        case DispatchCmd(cmd) =>
          conns.foreach {
            _ ! cmd
          }
          Behaviors.same

        case Print =>
          ctx.log.info(s"EntityID: $entityID")
          Behaviors.same
      }
    }

  def getEntityRef(
                    sharding: ClusterSharding,
                    entityID: String
                  ): EntityRef[Command] =
    sharding.entityRefFor(TypeKey, entityID)

  sealed trait Command extends KryoSerializer

  case class DispatchCmd(connectionCmd: ConnectionService.Command)
    extends Command

  case object Print extends Command

  val TypeKey = EntityTypeKey[Command]("UserEntity")

  case class AddConnection(conn: ActorRef[ConnectionService.Command])
    extends Command

  case class RemoveConnection(conn: ActorRef[ConnectionService.Command])
    extends Command

}
