package domain.model

import domain.common.Entity

object User {
  sealed trait UserEntity extends Entity

  case class UserIdentityOnly(id: String) extends UserEntity {
    override type ID = String
  }

  case class User(id: String, name: String) extends UserEntity {
    override type ID = String
  }
}
