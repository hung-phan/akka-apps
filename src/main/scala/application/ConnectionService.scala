package application

import akka.actor.typed.Behavior
import akka.actor.typed.scaladsl.Behaviors
import infrastructure.common.KryoSerializable

object ConnectionService {
  sealed trait Command extends KryoSerializable
  case class ForwardMsg(msg: String) extends Command
  case object Stop extends Command

  trait SocketConnection {
    def send(msg: String): Unit
  }

  def apply(socket: SocketConnection): Behavior[Command] = {
    Behaviors.receiveMessagePartial {
      case ForwardMsg(msg) =>
        socket.send(msg)
        Behaviors.same

      case Stop =>
        Behaviors.stopped
    }
  }
}
