//// Start writing your ScalaFiddle code here
//import play.api.libs.json.{Json, OWrites, Writes}
//import shapeless._
//import shapeless.labelled._
//
//sealed trait Drawing
//
//case class Circle(radius: Int) extends Drawing
//
//case class Rectangle(width: Int, height: Int) extends Drawing
//
//case class Group(drawings: Seq[Drawing]) extends Drawing
//
//implicit def writeHNil = OWrites[HNil] { _ => Json.obj() }
//implicit def owriteHList[Key <: Symbol, Head, Tail <: HList](implicit
//    fieldType: Witness.Aux[Key],
//    writesHead: Lazy[Writes[Head]],
//    writesTail: Lazy[OWrites[Tail]]
//) = {
//
//  val name = fieldType.value.name
//
//  OWrites[FieldType[Key, Head] :: Tail] {
//    case head :: tail =>
//      Json.obj(name -> writesHead.value.writes(head)) ++
//        writesTail.value.writes(tail)
//  }
//}
//implicit def owritesGeneric[A, AsHListRep](implicit
//    gen: LabelledGeneric.Aux[A, AsHListRep],
//    owritesHList: Lazy[OWrites[AsHListRep]]
//) =
//  OWrites[A] { a =>
//    owritesHList.value.writes(gen.to(a))
//  }
//implicit def owriteCoproduct[Key <: Symbol, Left, Right <: Coproduct](implicit
//    fieldType: Witness.Aux[Key],
//    owritesLeft: Lazy[OWrites[Left]],
//    owritesRight: Lazy[OWrites[Right]]
//) = {
//
//  val typeName = fieldType.value.name
//  OWrites[FieldType[Key, Left] :+: Right] {
//    case Inl(left) =>
//      Json.obj("type" -> fieldType.value.name) ++ owritesLeft.value.writes(
//        left
//      )
//    case Inr(right) => owritesRight.value.writes(right)
//  }
//}
//implicit def owriteCnil =
//  OWrites[CNil] { _ =>
//    throw new Exception("shouldn't happen")
//  }
//
//val result = Json.toJson(Group(Seq(Rectangle(1, 2), Circle(9)))).toString
//
//println(result)

import infrastructure.serializer.JsonSerializer._

val iceCream = IceCream("Sundae", 1, false)
val encoder = implicitly[JsonEncoder[IceCream]]

case class IceCream(name: String, numCherries: Int, inCone: Boolean)

encoder.encode(iceCream)
