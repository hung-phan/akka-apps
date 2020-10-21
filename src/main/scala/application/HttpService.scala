package application

import akka.actor.typed.{ActorRef, ActorSystem, Scheduler}
import akka.http.scaladsl.model.{
  AttributeKeys,
  ContentTypes,
  HttpEntity,
  StatusCodes
}
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import akka.http.scaladsl.server.directives.RouteDirectives.complete

import scala.concurrent.ExecutionContext
import scala.language.postfixOps
import scala.util.{Failure, Success}

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
          |  const exampleSocket = new WebSocket("ws://localhost:8080/ws?userid=1");
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
      guardian: ActorRef[MainSystem.Command]
  )(implicit
      system: ActorSystem[_],
      ec: ExecutionContext,
      scheduler: Scheduler
  ): Route = {
    path("ws") {
      extractRequestContext { reqCtx =>
        parameters("userid", "protocol".withDefault("text")) {
          (userId, protocol) =>
            reqCtx.request.attribute(AttributeKeys.webSocketUpgrade) match {
              case Some(upgrade) =>
                onComplete(
                  UserService.createWsFlow(protocol, userId, guardian)
                ) {
                  case Success(flow) => complete(upgrade.handleMessages(flow))
                  case Failure(ex)   => failWith(ex)
                }
              case None =>
                complete(
                  StatusCodes.BadRequest,
                  "Not a valid websocket request!"
                )
            }
        }
      }
    }
  }

  def getRoutes(
      guardian: ActorRef[MainSystem.Command]
  )(implicit
      system: ActorSystem[_],
      ec: ExecutionContext,
      scheduler: Scheduler
  ): Route =
    getMainPageRoute() ~ getWebsocketRoute(guardian)
}
