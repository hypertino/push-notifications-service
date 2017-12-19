package com.hypertino.services.pushnotifications

import com.hypertino.hyperbus.Hyperbus
import com.hypertino.hyperbus.model.{MessagingContext, NotFound}
import com.hypertino.hyperbus.subscribe.Subscribable
import com.hypertino.hyperbus.transport.api.ServiceRegistrator
import com.hypertino.hyperbus.transport.registrators.DummyRegistrator
import com.hypertino.mock.hyperstorage.HyperStorageMock
import com.hypertino.service.config.ConfigLoader
import com.hypertino.service.control.api.Service
import com.hypertino.services.hyperstorage.api.ContentGet
import com.typesafe.config.Config
import monix.execution.{Cancelable, Scheduler}
import org.scalatest.{BeforeAndAfterAll, BeforeAndAfterEach, Matchers, TestSuite}
import org.scalatest.concurrent.{Eventually, ScalaFutures}
import org.scalatest.time.{Seconds, Span}
import scaldi.Module

import scala.concurrent.duration._
import scala.collection.mutable

trait TestServiceBase extends TestSuite with Module with Subscribable
  with BeforeAndAfterAll
  with BeforeAndAfterEach
  with ScalaFutures
  with Eventually
  with Matchers {

  implicit val patience = PatienceConfig(scaled(Span(20, Seconds)))

  protected implicit val _scheduler = monix.execution.Scheduler.Implicits.global
  bind [Config] to ConfigLoader()
  bind [Scheduler] to _scheduler
  bind [Hyperbus] to injected[Hyperbus]
  bind [ServiceRegistrator] to DummyRegistrator

  protected val _hyperbus = inject[Hyperbus]

  protected val _handlers = mutable.ArrayBuffer[Cancelable]()
  protected val _services = mutable.ArrayBuffer[Service]()
  protected implicit val mcx = MessagingContext.empty
  protected val _hyperStorageMock = new HyperStorageMock(_hyperbus, _scheduler)
  implicit val so = com.hypertino.hyperbus.serialization.SerializationOptions.default
  import so._

  def start(handlers: Seq[Cancelable] = Seq.empty, services: Seq[Service] = Seq.empty): Unit = {
    _handlers ++= handlers
    _services ++= services

    eventually {
      _hyperbus.ask(ContentGet("not-existing")).runAsync.failed.futureValue shouldBe a[NotFound[_]]
    }
    Thread.sleep(1500)
  }

  override def afterAll() {
    _services.foreach(_.stopService(false, 10.seconds).futureValue)
    _handlers.foreach(_.cancel())
    _hyperbus.shutdown(10.seconds).runAsync.futureValue
  }

  override def beforeEach(): Unit = {
    _hyperStorageMock.reset()
    Thread.sleep(1000)
  }

  override def afterEach(): Unit = {
    //_hyperStorageMock.reset()
    Thread.sleep(1000)
  }
}
