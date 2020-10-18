package application

import akka.actor.typed.ActorRef
import akka.actor.typed.scaladsl.ActorContext
import akka.cluster.sharding.typed.ShardingEnvelope
import akka.http.scaladsl.model.{ContentTypes, HttpEntity}
import akka.http.scaladsl.server.Directives.{parameter, _}
import akka.http.scaladsl.server.Route
import akka.stream.Materializer
import application.UserService.UserCommand

import scala.language.postfixOps

object HttpService {
  private def getMainPageRoute(): Route =
    (pathEndOrSingleSlash & get) {
      complete(
        HttpEntity(
          ContentTypes.`text/html(UTF-8)`,
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
        )
      )
    }

  private def getWebsocketRoute(
      ctx: ActorContext[_],
      userShardRegion: ActorRef[ShardingEnvelope[UserCommand]]
  )(implicit materializer: Materializer): Route =
    (path("ws") & parameter(
      Symbol("protocol").as[String],
      Symbol("userId").as[String]
    )) { (protocol: String, userId: String) =>
      handleWebSocketMessages(
        UserService.createWsFlow(protocol, userId, ctx, userShardRegion)
      )
    }

  def getRoutes(
      ctx: ActorContext[_],
      userShardRegion: ActorRef[ShardingEnvelope[UserCommand]]
  )(implicit materializer: Materializer): Route =
    getMainPageRoute() ~ getWebsocketRoute(ctx, userShardRegion)
}
