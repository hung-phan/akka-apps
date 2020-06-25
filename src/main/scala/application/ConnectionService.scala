package application

import akka.actor.typed.Behavior
import akka.actor.typed.scaladsl.Behaviors
import domain.model.Connection.{Command, ForwardMsg}

object ConnectionService {
  trait SocketConnection {
    def send(msg: String): Unit
  }

  def actor(socket: SocketConnection): Behavior[Command] =
    Behaviors.receiveMessagePartial {
      case ForwardMsg(msg) =>
        socket.send(msg.stringify)
        Behaviors.same
    }
}
