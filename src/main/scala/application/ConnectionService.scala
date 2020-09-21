package application

import akka.actor.typed.Behavior
import akka.actor.typed.scaladsl.Behaviors
import infrastructure.common.KryoSerializer

object ConnectionService {
  sealed trait Command extends KryoSerializer
  case class ForwardMsg(msg: String) extends Command
  case object Stop extends Command

  trait SocketConnection {
    def send(msg: String): Unit
  }

  def apply(socket: SocketConnection): Behavior[Command] = {
    Behaviors.setup { ctx =>
      Behaviors.receiveMessagePartial {
        case ForwardMsg(msg) =>
          socket.send(msg)
          Behaviors.same

        case Stop =>
          Behaviors.stopped
      }
    }
  }
}
