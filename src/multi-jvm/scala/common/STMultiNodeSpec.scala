package common

import akka.actor.typed.scaladsl.adapter._
import akka.cluster.typed.{Cluster, Join}
import akka.remote.testconductor.RoleName
import akka.remote.testkit.{MultiNodeSpec, MultiNodeSpecCallbacks}
import org.scalatest.BeforeAndAfterAll
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpecLike

import scala.language.implicitConversions

/**
  * Hooks up MultiNodeSpec with ScalaTest
  */
trait STMultiNodeSpec
    extends MultiNodeSpecCallbacks
    with AnyWordSpecLike
    with Matchers
    with BeforeAndAfterAll {
  this: MultiNodeSpec =>
  override def beforeAll() = multiNodeSpecBeforeAll()

  override def afterAll() = multiNodeSpecAfterAll()

  // Might not be needed anymore if we find a nice way to tag all logging from a node
  override implicit def convertToWordSpecStringWrapper(
    s: String
  ): WordSpecStringWrapper =
    new WordSpecStringWrapper(s"$s (on node '${this.myself.name}', $getClass)")

  implicit val typedSystem = system.toTyped

  val cluster = Cluster(typedSystem)

  def join(from: RoleName, to: RoleName): Unit = {
    runOn(from) {
      cluster.manager ! Join(node(to).address)
    }
    enterBarrier(from.name + "-joined")
  }
}
