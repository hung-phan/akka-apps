package application

import akka.actor.typed.javadsl.ActorContext
import akka.cluster.sharding.typed.scaladsl.ClusterSharding
import akka.http.scaladsl.model.{ContentTypes, HttpEntity}
import akka.http.scaladsl.server.Directives.{parameter, _}
import akka.http.scaladsl.server.Route
import akka.stream.Materializer
import domain.common.ID
import domain.model.UserModel.UserWithIdentityOnly

import scala.language.postfixOps

class HttpService extends App {
  def getRoutes(sharding: ClusterSharding, ctx: ActorContext[_])(implicit
      materializer: Materializer
  ): Route = {
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

    (pathEndOrSingleSlash & get) {
      complete(HttpEntity(ContentTypes.`text/html(UTF-8)`, html))
    } ~
      path("ws") {
        (parameter(Symbol("userIdentifier").as[String]) &
          parameter(Symbol("protocol").as[String])) {
          (userIdentity: String, protocol: String) =>
            handleWebSocketMessages(
              UserService.createWebSocketFlow(
                UserService.getProtocol(protocol),
                ctx,
                UserWithIdentityOnly(ID(userIdentity)),
                sharding
              )
            )
        }
      }
  }
}
