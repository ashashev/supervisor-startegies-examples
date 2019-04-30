package com.example

import akka.actor.{ Actor, ActorLogging, Props }

class LifecycleLogger extends Actor with ActorLogging {
  import LifecycleMonitorable._

  def receive: Receive = {
    case e: LifecycleEvent =>
      log.warning(s"\n    ${sender().path}: $e\n")
  }
}

object LifecycleLogger {
  def props(): Props = Props(new LifecycleLogger)
}
