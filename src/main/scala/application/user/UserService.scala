package application.user

import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.{ActorRef, Behavior}
import akka.cluster.sharding.typed.scaladsl.{
  ClusterSharding,
  EntityRef,
  EntityTypeKey
}
import application.user.ConnectionService.SendClientMsg
import infrastructure.serializer.KryoSerializable

object UserService {
  sealed trait Command extends KryoSerializable
  case class AddConn(conn: ActorRef[ConnectionService.Command]) extends Command
  case class DeleteConn(conn: ActorRef[ConnectionService.Command])
      extends Command
  case class Broadcast(msg: String) extends Command
  case class Process(msg: String, replyTo: ActorRef[ConnectionService.Command])
      extends Command

  val TypeKey = EntityTypeKey[Command]("UserEntity")

  def fromEntityRef(
      entityID: String,
      sharding: ClusterSharding
  ): EntityRef[Command] = sharding.entityRefFor(TypeKey, entityID)

  def apply(
      entityID: String,
      shard: ActorRef[ClusterSharding.ShardCommand],
      conns: List[ActorRef[ConnectionService.Command]]
  ): Behavior[Command] =
    Behaviors
      .receiveMessagePartial {
        case AddConn(conn) =>
          UserService(
            entityID,
            shard,
            conns.find(_.path == conn.path) match {
              case Some(_) => conns
              case None    => conn :: conns
            }
          )

        case DeleteConn(conn) =>
          UserService(entityID, shard, conns.filter(_.path != conn.path))

        case Broadcast(msg) =>
          val cmd = SendClientMsg(msg)
          conns.foreach { _ ! cmd }
          Behaviors.same

        case Process(msg, _) =>
          println(msg)
          Behaviors.same
      }
}
