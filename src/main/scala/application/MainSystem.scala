package application

import akka.Done
import akka.actor.CoordinatedShutdown
import akka.actor.typed.scaladsl.AskPattern.Askable
import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.{ActorRef, ActorSystem, Behavior}
import akka.cluster.sharding.typed.ShardingEnvelope
import akka.cluster.sharding.typed.scaladsl.{ClusterSharding, Entity}
import akka.http.scaladsl.Http
import akka.stream.Materializer
import akka.util.Timeout
import infrastructure.serializer.KryoSerializable

import scala.concurrent.duration._
import scala.language.postfixOps

object MainSystem extends App {
  sealed trait Command extends KryoSerializable
  case class CreateUserSocket(
      userId: String,
      socket: ActorRef[UserService.UserSocketCommand],
      replyTo: ActorRef[CommandResp]
  ) extends Command
  case class Stop(replyTo: ActorRef[Done]) extends Command

  sealed trait CommandResp extends KryoSerializable
  case class CreateUserSocketResp(ref: ActorRef[UserService.UserSocketCommand])
      extends CommandResp

  def guardian(): Behavior[Command] =
    Behaviors.setup { ctx =>
      implicit val system = ctx.system
      implicit val ec = system.executionContext
      implicit val scheduler = system.scheduler
      implicit val materializer = Materializer(system)

      lazy val sharding = ClusterSharding(ctx.system)
      lazy val userShardRegion
          : ActorRef[ShardingEnvelope[UserService.UserCommand]] =
        sharding.init(
          Entity(UserService.TypeKey)(entityContext =>
            UserService.user(entityContext.entityId, entityContext.shard)
          ).withStopMessage(UserService.Terminate)
        )
      lazy val chatShardRegion
          : ActorRef[ShardingEnvelope[ChatService.Command]] = {
        sharding.init(
          Entity(ChatService.TypeKey)(entityContext =>
            ChatService(entityContext.entityId, entityContext.shard)
          ).withStopMessage(ChatService.Terminate)
        )
      }

      Http()
        .newServerAt("localhost", 3000)
        .bindFlow(HttpService.getRoutes(ctx.self))
        .map(_.addToCoordinatedShutdown(20 seconds))

      Behaviors.receiveMessagePartial {
        case CreateUserSocket(userId, socket, replyTo) =>
          replyTo ! CreateUserSocketResp(
            ctx.spawnAnonymous(
              UserService.userSocket(userId, socket, userShardRegion)
            )
          )
          Behaviors.same

        case Stop(ref) =>
          system.terminate()
          ref ! Done
          Behaviors.empty
      }
    }

  val system = ActorSystem(guardian(), "ChatSystem")

  CoordinatedShutdown(system).addTask(
    CoordinatedShutdown.PhaseBeforeServiceUnbind,
    "gracefulShutdown"
  ) { () =>
    implicit val timeout = Timeout(25 seconds)
    implicit val scheduler = system.scheduler

    system ? (replyTo => Stop(replyTo))
  }
}
