package com.example

import scala.concurrent.duration._

import akka.actor.{ ActorRef, ActorSystem, SupervisorStrategy }
import akka.testkit.{ ImplicitSender, TestEventListener, TestKit, TestProbe }

import org.scalatest.{ BeforeAndAfterAll, WordSpecLike, Matchers }

import com.example.LifecycleMonitorable._
import com.typesafe.config.ConfigFactory
import akka.testkit.EventFilter
import akka.testkit.TestActorRef
import akka.actor.OneForOneStrategy
import akka.actor.Terminated
import akka.actor.AllForOneStrategy

class TestSupervisorSpec(_system: ActorSystem)
  extends TestKit(_system)
  with Matchers
  with WordSpecLike
  with BeforeAndAfterAll {

  def this() = this(ActorSystem("MonitorSystem", ConfigFactory.parseString(
    """akka {
      |  stdout-loglevel = "OFF"
      |  loglevel = "OFF"
      |}""".stripMargin)))

  override def afterAll: Unit = {
    shutdown(system)
  }

  def withSupervisor(strategy: SupervisorStrategy, loglevel: String = "ERROR")
    (testCode: (ActorSystem, ActorRef) => Any) {

    val testSystem = ActorSystem("TestSystem", ConfigFactory.parseString(
      s"""akka {
        |  event-handlers = ["akka.testkit.TestEventListener"]
        |  loggers = ["akka.testkit.TestEventListener"]
        |  stdout-loglevel = "OFF"
        |  loglevel = "$loglevel"
        |  actor {
        |    debug {
        |      autoreceive = on
        |      lifecycle = on
        |    }
        |  }
        |}""".stripMargin))

    val supervisor = testSystem.actorOf(TestSupervisor.props(strategy))

    try {
        testCode(testSystem, supervisor)
    } finally {
      testSystem.terminate()
    }
  }

  "The default supervisor strategy" when {
    "a child throw Exception" should {
      "restart it" in withSupervisor(SupervisorStrategy.defaultStrategy) { (testSystem, supervisor) =>
        val lifeMonitor = TestProbe()
        val watcher = TestProbe()
        implicit val sender: ActorRef = watcher.ref

        supervisor ! TestSupervisor.Supervise(FailingActor.props(lifeMonitor.ref), "fallen")
        lifeMonitor.expectMsg(PreStartEvent)
        val fallen = watcher.expectMsgType[ActorRef]

        info("check that the fallen actor works")
        fallen ! FailingActor.Ping
        watcher.expectMsg(FailingActor.Pong)

        info("expect one error in the logging system")
        val e = new Exception()
        EventFilter[Exception](occurrences = 1).intercept({
          fallen ! FailingActor.Throw(e)
        })(testSystem)

        info("Then the lifeMonitor should get four messages: PreRestartEvent, PostStopEvent, PostRestartEvent, PreStartEvent")
        lifeMonitor.expectMsgAllOf(
          PreRestartEvent(e, Option(FailingActor.Throw(e))),
          PostStopEvent,
          PostRestartEvent(e),
          PreStartEvent)

        info("The reference on the fallen actor shouldn't change and the actor should works after restart")
        fallen ! FailingActor.Ping
        watcher.expectMsg(FailingActor.Pong)

        fallen ! Unsubscribe(lifeMonitor.ref)
      }
    }

    "a child throw Error" should {
      "escalate error to up" in withSupervisor(SupervisorStrategy.defaultStrategy, loglevel = "OFF") { (testSystem, supervisor) =>
        val lifeMonitor = TestProbe()
        val watcher = TestProbe()
        implicit val sender: ActorRef = watcher.ref

        supervisor ! TestSupervisor.Supervise(FailingActor.props(lifeMonitor.ref), "fallen")
        lifeMonitor.expectMsg(PreStartEvent)
        val fallen = watcher.expectMsgType[ActorRef]

        watcher.watch(fallen)

        val e = new Error()
        fallen ! FailingActor.Throw(e)

        info("Then the lifeMonitor should get only PostStopEvent message")
        lifeMonitor.expectMsgAllOf(PostStopEvent)
        lifeMonitor.expectNoMessage()

        info("The fallen actor should be stopped")
        watcher.expectTerminated(fallen)

        info("The system with fallen actor should be terminated, because the error was escalated and unhandled.")
        assert(testSystem.whenTerminated.isCompleted)

        watcher.unwatch(fallen)
      }
    }
  }

  "The stopping supervisor strategy (resembles Erlang)" when {
    "a child throw Exception" should {
      "stop it" in withSupervisor(SupervisorStrategy.stoppingStrategy) { (testSystem, supervisor) =>
        val lifeMonitor = TestProbe()
        val watcher = TestProbe()
        implicit val sender: ActorRef = watcher.ref

        supervisor ! TestSupervisor.Supervise(FailingActor.props(lifeMonitor.ref), "fallen")
        lifeMonitor.expectMsg(PreStartEvent)
        val fallen = watcher.expectMsgType[ActorRef]

        watcher.watch(fallen)

        info("expect one error in the logging system")
        val e = new Exception()
        EventFilter[Exception](occurrences = 1).intercept({
          fallen ! FailingActor.Throw(e)
        })(testSystem)

        info("Then the lifeMonitor should get only PostStopEvent message")
        lifeMonitor.expectMsgAllOf(PostStopEvent)
        lifeMonitor.expectNoMessage()

        info("The fallen actor should be stopped")
        watcher.expectTerminated(fallen)

        info("The system with fallen actor should work.")
        assert(!testSystem.whenTerminated.isCompleted)

        watcher.unwatch(fallen)
      }
    }

    "a child throw Error" should {
      "escalate error to up" in withSupervisor(SupervisorStrategy.stoppingStrategy, loglevel = "OFF") { (testSystem, supervisor) =>
        val lifeMonitor = TestProbe()
        val watcher = TestProbe()
        implicit val sender: ActorRef = watcher.ref

        supervisor ! TestSupervisor.Supervise(FailingActor.props(lifeMonitor.ref), "fallen")
        lifeMonitor.expectMsg(PreStartEvent)
        val fallen = watcher.expectMsgType[ActorRef]

        watcher.watch(fallen)

        val e = new Error()
        fallen ! FailingActor.Throw(e)

        info("Then the lifeMonitor should get only PostStopEvent message")
        lifeMonitor.expectMsgAllOf(PostStopEvent)
        lifeMonitor.expectNoMessage()

        info("The fallen actor should be stopped")
        watcher.expectTerminated(fallen)

        info("The system with fallen actor should be terminated, because the error was escalated and unhandled.")
        assert(testSystem.whenTerminated.isCompleted)

        watcher.unwatch(fallen)
      }
    }
  }

  "The OneForOneStrategy applies the fault handling Directive" when {
    "a child actor failed to that actor only" in withSupervisor(
      new OneForOneStrategy({case _: Throwable => SupervisorStrategy.Stop}), loglevel = "OFF") { (testSystem, supervisor) =>
        val watcher = TestProbe()
        implicit val sender = watcher.ref

        supervisor ! TestSupervisor.Supervise(FailingActor.props(), "fallen1")
        val fallen1 = watcher.expectMsgType[ActorRef]
        watcher.watch(fallen1)

        supervisor ! TestSupervisor.Supervise(FailingActor.props(), "fallen2")
        val fallen2 = watcher.expectMsgType[ActorRef]
        watcher.watch(fallen2)

        info("The fallen1 should be stopped")
        fallen1 ! FailingActor.ThrowError
        watcher.expectTerminated(fallen1)

        info("The fallen2 should work")
        fallen2 ! FailingActor.Ping
        watcher.expectMsg(FailingActor.Pong)

        watcher.unwatch(fallen1)
        watcher.unwatch(fallen2)
    }
  }

  "The AllForOneStrategy applies the fault handling Directive" when {
    "a child actor failed to all childs" in withSupervisor(
      new AllForOneStrategy({case _: Throwable => SupervisorStrategy.Stop}), loglevel = "OFF") { (testSystem, supervisor) =>
        val watcher1 = TestProbe()
        val watcher2 = TestProbe()
        implicit val sender = watcher1.ref

        supervisor ! TestSupervisor.Supervise(FailingActor.props(), "fallen1")
        val fallen1 = watcher1.expectMsgType[ActorRef]
        watcher1.watch(fallen1)

        supervisor ! TestSupervisor.Supervise(FailingActor.props(), "fallen2")
        val fallen2 = watcher1.expectMsgType[ActorRef]
        watcher2.watch(fallen2)

        info("The fallen1 and fallen2 should be stopped")
        fallen1 ! FailingActor.ThrowError
        watcher1.expectTerminated(fallen1)
        watcher2.expectTerminated(fallen2)

        watcher1.unwatch(fallen1)
        watcher2.unwatch(fallen2)
    }
  }

  "A actor" should {
    "lost his state" when {
      "restarted" in 
        withSupervisor(SupervisorStrategy.defaultStrategy, loglevel = "OFF") { (testSystem, supervisor) =>
        val mon1 = TestProbe()
        val mon2 = TestProbe()
        val watcher = TestProbe()
        implicit val sender = watcher.ref

        val testSystem = ActorSystem()

        supervisor ! TestSupervisor.Supervise(FailingActor.props(mon1.ref), "fallen")
        val fallen = watcher.expectMsgType[ActorRef]
        fallen ! Subscribe(mon2.ref)

        fallen ! WhoSubscribe
        watcher.expectMsg(Set(mon1.ref, mon2.ref))

        fallen ! FailingActor.ThrowException

        fallen ! WhoSubscribe
        watcher.expectMsg(Set(mon1.ref))
      }
    }
  }

}
