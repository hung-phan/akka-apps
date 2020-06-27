package application

import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.{ActorRef, Behavior}
import domain.common.{JSONSerializable, SerializableMsg}

object ConnectionService {
  sealed trait Command extends SerializableMsg
  case class ForwardMsg(msg: JSONSerializable) extends Command

  type ConnectionActorRef = ActorRef[Command]

  case class ConnectionManager(conns: Set[ConnectionActorRef]) {
    def :+(conn: ConnectionActorRef) =
      this.copy(conns = conns + conn)

    def :-(conn: ConnectionActorRef) =
      this.copy(conns = conns - conn)

    def dispatch(cmd: Command): Unit = {
      conns.foreach { _ ! cmd }
    }
  }

  trait SocketConnection {
    def send(msg: String): Unit
  }

  def apply(socket: SocketConnection): Behavior[Command] =
    Behaviors.receiveMessagePartial {
      case ForwardMsg(msg) =>
        socket.send(msg.stringify)
        Behaviors.same
    }
}
