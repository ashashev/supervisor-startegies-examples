package com.example

import akka.actor.{ Actor, ActorRef }

trait LifecycleMonitorable extends Actor {
  import LifecycleMonitorable._

  protected var subscribers = Set.empty[ActorRef]

  def subUnsub: Actor.Receive = {
    case Subscribe(subscriber)   => subscribers += subscriber
    case Unsubscribe(subscriber) => subscribers -= subscriber
    case WhoSubscribe            => sender() ! subscribers
  }

  override def unhandled(message: Any): Unit = {
    if (subUnsub.isDefinedAt(message)) subUnsub(message)
    else super.unhandled(message)
  }

  override def preStart(): Unit = {
    subscribers.foreach(_ ! PreStartEvent)
    super.preStart()
  }

  override def preRestart(reason: Throwable, message: Option[Any]): Unit = {
    subscribers.foreach(_ ! PreRestartEvent(reason, message))
    super.preRestart(reason, message)
  }

  override def postRestart(reason: Throwable): Unit = {
    subscribers.foreach(_ ! PostRestartEvent(reason))
    super.postRestart(reason)
  }

  override def postStop(): Unit = {
    subscribers.foreach(_ ! PostStopEvent)
    super.postStop()
  }
}

object LifecycleMonitorable {
  case class Subscribe(subscriber: ActorRef)
  case class Unsubscribe(subscriber: ActorRef)
  case object WhoSubscribe

  sealed trait LifecycleEvent
  case object PreStartEvent extends LifecycleEvent
  case class PreRestartEvent(reason: Throwable, message: Option[Any]) extends LifecycleEvent
  case class PostRestartEvent(reason: Throwable) extends LifecycleEvent
  case object PostStopEvent extends LifecycleEvent
}
