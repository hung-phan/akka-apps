package common

import akka.actor.testkit.typed.scaladsl.ActorTestKit
import akka.actor.typed.scaladsl.adapter._
import akka.actor.typed.{ActorRef, Behavior}
import akka.cluster.sharding.typed.scaladsl.{
  ClusterSharding,
  Entity,
  EntityContext,
  EntityTypeKey
}
import akka.cluster.sharding.typed.{ClusterShardingSettings, ShardingEnvelope}
import akka.cluster.typed.{Cluster, Join}
import akka.remote.testconductor.RoleName
import akka.remote.testkit.{MultiNodeSpec, MultiNodeSpecCallbacks}
import akka.stream.Materializer
import org.scalatest.BeforeAndAfterAll
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpecLike

import scala.language.{implicitConversions, postfixOps}
import scala.sys.process._

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

  override def afterAll() = {
    multiNodeSpecAfterAll()
    // remove all test_data
    "rm -rf test_data test_ddata" !
  }

  // Might not be needed anymore if we find a nice way to tag all logging from a node
  override implicit def convertToWordSpecStringWrapper(
      s: String
  ): WordSpecStringWrapper =
    new WordSpecStringWrapper(s"$s (on node '${this.myself.name}', $getClass)")

  implicit val typedSystem = system.toTyped
  implicit val materializer = Materializer(typedSystem)
  implicit val sharding = ClusterSharding(typedSystem)

  lazy val cluster = Cluster(typedSystem)
  lazy val testKit = ActorTestKit(typedSystem)

  def initialParticipants = roles.size

  def join(from: RoleName, to: RoleName): Unit = {
    runOn(from) {
      cluster.manager ! Join(node(to).address)
    }

    enterBarrier(s"${from.name}-joined")
  }

  def startProxySharding[T](
      typeKey: EntityTypeKey[T],
      createBehaviour: EntityContext[T] => Behavior[T]
  ): ActorRef[ShardingEnvelope[T]] = {
    sharding.init(
      Entity(typeKey)(createBehaviour)
        .withSettings(ClusterShardingSettings(typedSystem))
    )
  }
}
