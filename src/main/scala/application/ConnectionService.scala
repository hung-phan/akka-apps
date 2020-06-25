package application

import akka.actor.typed.Behavior
import akka.actor.typed.scaladsl.Behaviors
import domain.common.SerializableData
import domain.model.Connection.Command

object ConnectionService {
  case class Send(msg: SerializableData) extends Command

  def Actor(): Behavior[Command] =
    Behaviors.receiveMessagePartial {
      case Send(msg) =>
        println(msg.serializedVal)
        Behaviors.same
    }
}
