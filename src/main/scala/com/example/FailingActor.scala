package com.example

import akka.actor.Actor
import akka.actor.ActorRef
import akka.actor.Props

class FailingActor(monitor: Option[ActorRef]) extends Actor with LifecycleMonitorable {
  import FailingActor._

  monitor.foreach(subscribers += _)

  def receive: Actor.Receive = {
    case "error" => throw new Error("error")
    case "exception" => throw new Exception("exception")
    case e: Throwable => throw e
  }
}

object FailingActor {
  def props(monitor: ActorRef) = Props(new FailingActor(Option(monitor)))
  def props() = Props(new FailingActor(None))
}
