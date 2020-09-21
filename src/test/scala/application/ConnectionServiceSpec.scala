package application

import akka.actor.testkit.typed.scaladsl.ScalaTestWithActorTestKit
import application.ConnectionService.{ForwardMsg, SocketConnection}
import org.scalamock.scalatest.MockFactory
import org.scalatest.wordspec.AnyWordSpecLike

class ConnectionServiceSpec
    extends ScalaTestWithActorTestKit
    with AnyWordSpecLike
    with MockFactory {
  "ConnectionService actor" should {
    "be able to forward message to client" in {
      val serializedData = "[1, 2, 3]"

      val mockSocketConnection = mock[SocketConnection]
      (mockSocketConnection.send _).expects(serializedData).once()

      val connectionActor = spawn(ConnectionService(mockSocketConnection))
      connectionActor ! ForwardMsg(serializedData)
    }
  }
}
