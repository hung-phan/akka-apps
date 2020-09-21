package domain.common

case class ID[T](value: T)
case class MsgType[T](value: T)

trait Entity[T] {
  val id: ID[T]
}
