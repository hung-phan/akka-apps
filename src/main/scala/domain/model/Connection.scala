package domain.model

import akka.actor.typed.ActorRef
import domain.common.{JSONSerializable, SerializableMsg}

object Connection {
  sealed trait Command extends SerializableMsg
  case class ForwardMsg(msg: JSONSerializable) extends Command

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
