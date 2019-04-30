package com.example

import scala.concurrent.duration._
import scala.language.postfixOps

import akka.actor.{ ActorRef, ActorSystem, SupervisorStrategy }
import akka.testkit.{ TestKit, TestProbe }

import org.scalatest.{ BeforeAndAfterAll, WordSpecLike, Matchers }

class TestSupervisorSpec(_system: ActorSystem)
  extends TestKit(_system)
  with Matchers
  with WordSpecLike
  with BeforeAndAfterAll {

  def this() = this(ActorSystem("MonitorSystem"))

  override def afterAll: Unit = {
    shutdown(system)
  }

  def withSupervisor(strategy: SupervisorStrategy)
    (testCode: ActorRef => Any) {

    val testSystem = ActorSystem("TestSystem")
    val supervisor = testSystem.actorOf(TestSupervisor.props())

    try {
      testCode(supervisor)
    } finally {
      testSystem.terminate()
    }
  }

  "Default supervisor strategy" should {
    "restart child actor if it throw Exception" in
        withSupervisor(SupervisorStrategy.defaultStrategy) { supervisor =>

      val monitor = TestProbe()
      val watcher = TestProbe()

    }
  }
}
