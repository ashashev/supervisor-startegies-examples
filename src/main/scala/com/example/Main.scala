package com.example

import scala.concurrent.duration._
import scala.language.postfixOps

import akka.actor.{ ActorRef, ActorSystem }
import akka.pattern.ask
import akka.util.Timeout

import com.example.TestSupervisor.Supervise

object Main extends App {
  val system = ActorSystem("MainSystem")
  val monitorSystem = ActorSystem("MonitorSystem")
  val supervisor = system.actorOf(TestSupervisor.props(), "supervisor")
  val monitor = monitorSystem.actorOf(LifecycleLogger.props(), "lifecycle-monitor")

  supervisor ! LifecycleMonitorable.Subscribe(monitor)

  implicit val ec = system.dispatcher
  implicit val timeout = Timeout(5 seconds)

  val child = ask(supervisor, Supervise(FailingActor.props(monitor), "faillen")).mapTo[ActorRef]

  def commandCycle(): Unit = {
    println(
      """Select action:
        |1. send exception
        |2. send error
        |0. exit""".stripMargin
    )
    print("> ")
    io.StdIn.readInt() match {
      case 1 =>
        child.map(_ ! FailingActor.ThrowException)
        commandCycle()
      case 2 =>
        child.map(_ ! FailingActor.ThrowError)
        commandCycle()
      case 0 =>
        system.terminate()
        system.whenTerminated.map(_ => monitorSystem.terminate())
      case _ =>
        commandCycle()
    }
  }

  commandCycle()
}
