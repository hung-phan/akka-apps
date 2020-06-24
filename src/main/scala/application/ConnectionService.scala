package application

import akka.actor.typed.Behavior
import akka.actor.typed.scaladsl.Behaviors
import domain.model.Connection.{Command, ConnectionData}

object ConnectionService {
  case class Send(msg: ConnectionData) extends Command

  def Actor(): Behavior[Command] =
    Behaviors.receiveMessagePartial {
      case Send(msg) =>
        println(msg.serializedVal)
        Behaviors.same
    }
}
