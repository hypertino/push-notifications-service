package com.hypertino.services.pushnotifications

import com.hypertino.hyperbus.Hyperbus
import com.hypertino.hyperbus.model.ResponseBase
import com.hypertino.hyperbus.subscribe.Subscribable
import com.hypertino.service.control.api.Service
import com.hypertino.services.apns.api.ApnsPost
import com.typesafe.scalalogging.StrictLogging
import monix.eval.Task
import monix.execution.Scheduler
import scaldi.Injectable

import scala.concurrent.Future
import scala.concurrent.duration.FiniteDuration

class ServiceMock(hyperbus: Hyperbus) extends Service
  with Subscribable
  with Injectable
  with StrictLogging
  with MockedMethods {
  implicit val scheduler: Scheduler = monix.execution.Scheduler.Implicits.global

  val handlers = hyperbus.subscribe(this, logger)

  var mockHandler: Option[MockedMethods] = None
  def onApnsPost(r: ApnsPost): Task[ResponseBase] = mockHandler map { it => it.onApnsPost(r) } getOrElse Task.raiseError(new IllegalStateException("Method handler is not provided"))

  def reset(): Unit ={
    mockHandler = None
  }

  override def stopService(controlBreak: Boolean, timeout: FiniteDuration): Future[Unit] = Future {
    handlers.foreach(_.cancel())
  }
}

trait MockedMethods {
  def onApnsPost(r: ApnsPost): Task[ResponseBase]
}
