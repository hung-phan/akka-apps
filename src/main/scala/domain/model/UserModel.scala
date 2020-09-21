package domain.model

import domain.common.{Entity, ID}

object UserModel {

  sealed trait UserEntity extends Entity[String]

  case class UserWithIdentityOnly(id: ID[String]) extends UserEntity

  case class UserWithFullInfos(id: ID[String], name: String) extends UserEntity

}
