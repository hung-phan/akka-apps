package domain.model

import akka.actor.typed.ActorRef
import domain.common.{Entity, ID}

object UserModel {
  sealed trait UserEntity extends Entity[String]

  case class UserIdentifier(id: ID[String]) extends UserEntity
  case class UserInfos(id: ID[String], name: String) extends UserEntity
  case class UserConnections[T](id: ID[String], conns: Set[ActorRef[T]])
      extends UserEntity
}
