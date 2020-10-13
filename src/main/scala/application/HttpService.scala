package application

import akka.actor.typed.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.{ContentTypes, HttpEntity}
import akka.http.scaladsl.server.Directives._
import akka.stream.Materializer

import scala.concurrent.Future
import scala.language.postfixOps

class HttpService extends App {
  def startServer(interface: String, port: Int)(implicit
      system: ActorSystem[_],
      materializer: Materializer
  ): Future[Http.ServerBinding] = {
    val html =
      """
        |<!DOCTYPE html>
        |<html lang="en">
        |<head>
        |    <meta charset="UTF-8">
        |    <title>Hello websocket</title>
        |</head>
        |<body>
        |<div id="content"></div>
        |<script>
        |  const exampleSocket = new WebSocket("ws://localhost:3000/ws");
        |
        |  console.log("starting websocket...");
        |
        |  exampleSocket.onmessage = event => {
        |    const newChild = document.createElement("div");
        |
        |    newChild.innerText = event.data;
        |
        |    document.getElementById("content").appendChild(newChild)
        |  };
        |
        |  exampleSocket.onopen = event => {
        |    exampleSocket.send("socket seems to be open...");
        |  };
        |</script>
        |</body>
        |</html>
        |""".stripMargin

    val routes =
      (pathEndOrSingleSlash & get) {
        complete(HttpEntity(ContentTypes.`text/html(UTF-8)`, html))
      }
//      } ~
//        (path("ws") | parameter(Symbol("userIdentifier").as[String])) {
//          (userIdentity: String) =>
////            handleWebSocketMessages()
//        }

    Http()
      .newServerAt(interface, port)
      .bindFlow(routes)
  }
}
