package domain

trait Entity {
  type ID
  val id: ID
}
