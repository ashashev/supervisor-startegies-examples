package com.example

import akka.actor.{ Actor, ActorRef, Props }

class FailingActor(monitor: Option[ActorRef]) extends Actor with LifecycleMonitorable {
  import FailingActor._

  monitor.foreach(subscribers += _)

  def receive: Actor.Receive = {
    case ThrowError     => throw new Error("Error from the FailingActor")
    case ThrowException => throw new Exception("Error from the FailingActor")
    case Throw(e)       => throw e
    case Ping           => sender() ! Pong
  }
}

object FailingActor {
  def props(monitor: ActorRef) = Props(new FailingActor(Option(monitor)))
  def props() = Props(new FailingActor(None))

  object ThrowError
  object ThrowException
  case class Throw(e: Throwable)

  object Ping
  object Pong
}
