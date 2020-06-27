package application

import akka.actor.testkit.typed.scaladsl.ScalaTestWithActorTestKit
import application.ConnectionService.SocketConnection
import domain.common.JSONSerializable
import domain.model.Connection.ForwardMsg
import org.scalamock.scalatest.MockFactory
import org.scalatest.wordspec.AnyWordSpecLike

class ConnectionServiceSpec
    extends ScalaTestWithActorTestKit
    with AnyWordSpecLike
    with MockFactory {
  "Connection actor" should {
    "be able to forward message for SerializableData" in {
      val serializedData = "[1, 2, 3]"

      class DummyData extends JSONSerializable {
        override def stringify: String = serializedData
      }

      val mockSocketConnection = mock[SocketConnection]
      (mockSocketConnection.send _).expects(serializedData).once()

      val connectionActor = spawn(ConnectionService(mockSocketConnection))
      connectionActor ! ForwardMsg(new DummyData)
    }
  }
}
