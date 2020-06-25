package domain.model

import akka.actor.typed.ActorRef
import domain.common.SerializableData

object Connection {
  sealed trait Command
  case class ForwardMsg(msg: SerializableData) extends Command

  type Connection = ActorRef[Command]

  case class ConnectionManager(conns: Set[Connection]) {
    def :+(conn: Connection) =
      this.copy(conns = conns + conn)

    def :-(conn: Connection) =
      this.copy(conns = conns - conn)

    def dispatch(cmd: Command): Unit = {
      conns.foreach { _ ! cmd }
    }
  }
}
