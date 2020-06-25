package domain.common

trait Entity {
  type ID
  val id: ID
}
