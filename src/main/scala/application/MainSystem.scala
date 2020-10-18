package application

import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.{ActorRef, Behavior}
import akka.cluster.sharding.typed.ShardingEnvelope
import akka.cluster.sharding.typed.scaladsl.{ClusterSharding, Entity}
import akka.stream.Materializer
import infrastructure.serializer.KryoSerializable

object MainSystem {
  sealed trait Command extends KryoSerializable

  def apply(): Behavior[Command] =
    Behaviors.setup { ctx =>
      implicit val materializer = Materializer(ctx.system)

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

      Behaviors.unhandled
    }
}
