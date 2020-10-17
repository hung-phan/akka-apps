package application

import akka.actor.ActorRefFactory
import akka.actor.typed.scaladsl.ActorContext
import akka.cluster.sharding.typed.scaladsl.ClusterSharding
import akka.http.scaladsl.model.ws.Message
import akka.http.scaladsl.model.{ContentTypes, HttpEntity}
import akka.http.scaladsl.server.Directives.{parameter, _}
import akka.http.scaladsl.server.Route
import akka.stream.Materializer
import akka.stream.scaladsl.{Flow, MergeHub, Sink}
import application.UserService.{HandleClientMsg, userConnection}

import scala.language.postfixOps

object HttpService {
  def getRoutes(ctx: ActorContext[_])(implicit
      materializer: Materializer,
      sharding: ClusterSharding
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
      (path("ws") & parameter(
        Symbol("protocol").as[String],
        Symbol("userIdentifier").as[String]
      )) { (protocol: String, userIdentity: String) =>
        val source = MergeHub.source[Message]
        val conn = ctx.spawn(
          userConnection(
            userIdentity,
            UserService.getProtocol(protocol),
            source.to(Sink.ignore).run()
          ),
          s"UserConnection(${userIdentity})"
        )

        handleWebSocketMessages(
          Flow.fromSinkAndSource(
            Sink.foreach[Message](conn ! HandleClientMsg(_)),
            source
          )
        )
      }
  }
}
