package com.example

import akka.actor.{ Actor, ActorLogging, Props, SupervisorStrategy }

class TestSupervisor(override val supervisorStrategy: SupervisorStrategy)
    extends Actor
    with ActorLogging
    with LifecycleMonitorable {

  def receive: Receive = {
    case TestSupervisor.Supervise(props, name) =>
      val child = context.actorOf(props, name)
      sender() ! child
  }
}

object TestSupervisor {
  case class Supervise(props: Props, name: String)

  def props(strategy: SupervisorStrategy = SupervisorStrategy.defaultStrategy) =
    Props(new TestSupervisor(strategy))
}
