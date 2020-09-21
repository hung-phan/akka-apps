package infrastructure.serializer

import shapeless.labelled.FieldType
import shapeless.{
  :+:,
  ::,
  CNil,
  Coproduct,
  HList,
  HNil,
  Inl,
  Inr,
  LabelledGeneric,
  Lazy,
  Witness
}

object JsonSerializer {
  sealed trait JsonValue

  trait JsonEncoder[A] {
    def encode(value: A): JsonValue
  }

  trait JsonObjectEncoder[A] extends JsonEncoder[A] {
    def encode(value: A): JsonObject
  }

  final case class JsonString(value: String) extends JsonValue
  final case class JsonNumber(value: Double) extends JsonValue
  final case class JsonBoolean(value: Boolean) extends JsonValue
  final case class JsonArray(items: List[JsonValue]) extends JsonValue
  final case class JsonObject(fields: List[(String, JsonValue)])
      extends JsonValue
  case object JsonNull extends JsonValue

  def createEncoder[A](func: A => JsonValue): JsonEncoder[A] =
    (value: A) => func(value)

  def createObjectEncoder[A](func: A => JsonObject): JsonObjectEncoder[A] =
    (value: A) => func(value)

  // Base type class instances
  implicit val stringEncoder: JsonEncoder[String] =
    createEncoder(JsonString(_))

  implicit val doubleEncoder: JsonEncoder[Double] =
    createEncoder(JsonNumber(_))

  implicit val intEncoder: JsonEncoder[Int] =
    createEncoder(JsonNumber(_))

  implicit val booleanEncoder: JsonEncoder[Boolean] =
    createEncoder(JsonBoolean(_))

  implicit def listEncoder[A](implicit
      encoder: JsonEncoder[A]
  ): JsonEncoder[List[A]] =
    createEncoder(list => JsonArray(list.map(encoder.encode)))

  implicit def optionEncoder[A](implicit
      encoder: JsonEncoder[A]
  ): JsonEncoder[Option[A]] =
    createEncoder(opt => opt.map(encoder.encode).getOrElse(JsonNull))

  implicit val hnilEncoder: JsonObjectEncoder[HNil] =
    createObjectEncoder(_ => JsonObject(Nil))

  implicit def hlistObjectEncoder[K <: Symbol, H, T <: HList](implicit
      witness: Witness.Aux[K],
      hEncoder: Lazy[JsonEncoder[H]],
      tEncoder: JsonObjectEncoder[T]
  ): JsonObjectEncoder[FieldType[K, H] :: T] = {
    val fieldName: String = witness.value.name

    createObjectEncoder { hlist =>
      val head = hEncoder.value.encode(hlist.head)
      val tail = tEncoder.encode(hlist.tail)

      JsonObject((fieldName, head) :: tail.fields)
    }
  }

  implicit def genericObjectEncoder[A, H <: HList](implicit
      generic: LabelledGeneric.Aux[A, H],
      hEncoder: Lazy[JsonObjectEncoder[H]]
  ): JsonEncoder[A] =
    createObjectEncoder(value => hEncoder.value.encode(generic.to(value)))

  implicit def coproductObjectEncoder[K <: Symbol, H, T <: Coproduct](implicit
      witness: Witness.Aux[K],
      hEncoder: Lazy[JsonEncoder[H]],
      tEncoder: JsonObjectEncoder[T]
  ): JsonObjectEncoder[FieldType[K, H] :+: T] = {
    val typeName = witness.value.name

    createObjectEncoder {
      case Inl(h) => JsonObject(List(typeName -> hEncoder.value.encode(h)))
      case Inr(t) => tEncoder.encode(t)
    }
  }

  implicit val cnilObjectEncoder: JsonObjectEncoder[CNil] =
    createObjectEncoder(_ => ???)
}
