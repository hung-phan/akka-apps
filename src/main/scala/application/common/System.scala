package application.common

import akka.actor.typed.ActorSystem

import scala.concurrent.duration._
import scala.language.postfixOps

object System {
  implicit val akkaRequestTimeout: akka.util.Timeout = 5 seconds
}
