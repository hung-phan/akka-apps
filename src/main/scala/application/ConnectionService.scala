package application

import akka.actor.typed.Behavior
import akka.actor.typed.scaladsl.Behaviors
import infrastructure.common.KryoSerializer

object ConnectionService {
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

  sealed trait Command extends KryoSerializer

  case class ForwardMsg(msg: String) extends Command

  trait SocketConnection {
    def send(msg: String): Unit
  }

  case object Stop extends Command

}
