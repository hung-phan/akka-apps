package application

import akka.actor.typed.Behavior
import akka.actor.typed.scaladsl.Behaviors
import domain.model.Connection.{Command, ForwardMsg}

object ConnectionService {
  def actor(): Behavior[Command] =
    Behaviors.receiveMessagePartial {
      case ForwardMsg(msg) =>
        println(msg.serializedVal)
        Behaviors.same
    }
}
