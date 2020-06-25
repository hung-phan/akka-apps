package domain.model

import domain.common.Entity
import Connection.ConnectionManager

object User {
  sealed trait UserEntity extends Entity

  case class UserIdentityOnly(id: String) extends UserEntity {
    override type ID = String
  }

  case class User(id: String, connectionManager: ConnectionManager)
      extends UserEntity {
    override type ID = String
  }
}
