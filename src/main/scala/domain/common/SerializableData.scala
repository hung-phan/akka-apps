package domain.common

trait SerializableData {
  type T
  def serializedVal: T
}
