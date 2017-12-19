package com.hypertino.services.pushnotifications

import com.hypertino.hyperbus.model.ResponseBase
import com.hypertino.service.control.api.Service
import com.hypertino.services.apns.api.ApnsPost
import com.typesafe.scalalogging.{Logger, StrictLogging}
import monix.eval.Task
import scaldi.Injectable

import scala.concurrent.{ExecutionContextExecutor, Future}
import scala.concurrent.duration.FiniteDuration

class ApnsServiceMock(private val apnsPostHandler: (ApnsPost) => Task[ResponseBase]) extends Service with Injectable with StrictLogging {
  logger.info(s"${getClass.getName} is STARTED")

  implicit val ec: ExecutionContextExecutor = scala.concurrent.ExecutionContext.Implicits.global

  val serviceLogger: Logger = this.logger

  def onApnsPost(r: ApnsPost): Task[ResponseBase] ={
    apnsPostHandler(r)
  }

  override def stopService(controlBreak: Boolean, timeout: FiniteDuration): Future[Unit] = Future {
    logger.info(s"${getClass.getName} is STOPPED")
  }
}
