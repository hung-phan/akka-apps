package domain.model

import akka.actor.typed.ActorRef

object Connection {
  trait Command
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
