package com.hypertino.services.pushnotifications

import java.lang

import com.hypertino.binders.value.{Obj, Text}
import com.hypertino.hyperbus.model.{Created, EmptyBody, NoContent, NotFound, Ok, ResponseBase}
import com.hypertino.hyperbus.serialization.SerializationOptions
import com.hypertino.hyperstorage.api.ContentGet
import com.hypertino.services.apns.api.{ApnsBadDeviceTokensFeedPost, BadToken}
import com.hypertino.services.hyperstorage.api.ViewGet
import com.hypertino.services.pushnotifications.api._
import monix.eval.Task
import org.scalatest.concurrent.{Eventually, ScalaFutures}
import org.scalatest.{FlatSpec, Matchers}

import scala.concurrent.ExecutionContextExecutor

class PushNotificationsServiceTest extends FlatSpec with TestServiceBase with Matchers with ScalaFutures with Eventually {
  start(
    services = Seq(
      new PushNotificationsService()
    )
  )
  import _hyperStorageMock._
  import so._

  "PushNotificationsService" should "save device token on TokenPut" in {
    val startBound = System.currentTimeMillis()

    _hyperbus.ask(TokenPut("test-token-a", new CreateNotificationToken(
      platform = Platforms.IOS,
      appName = "com.test-app",
      deviceToken = "test-device-token-1",
      userId = "user-1"
    ))).runAsync.futureValue shouldBe a[Created[_]]

    val (notificationTokenValue, revT) = hyperStorageContent(Db.notificationTokenPath("test-token-a"))

    revT shouldBe 1

    val notificationToken = notificationTokenValue.to[NotificationToken]

    notificationToken.tokenId shouldBe "test-token-a"
    notificationToken.appName shouldBe "com.test-app"
    notificationToken.createdAt should (be >= startBound and be <= System.currentTimeMillis())
    notificationToken.platform shouldBe Platforms.IOS
    notificationToken.deviceToken shouldBe "test-device-token-1"
    notificationToken.userId shouldBe "user-1"

    val (deviceTokenValue, revD) = hyperStorageContent(Db.notificationTokenPlatformTokenPath(Platforms.IOS, "test-device-token-1"))

    revD shouldBe 1
    deviceTokenValue.to[NotificationToken] shouldBe notificationToken

    val (userTokenValue, revU) = hyperStorageContent(Db.notificationUserTokensItemPath("user-1", "test-token-a"))

    revU shouldBe 1
    userTokenValue.to[NotificationToken] shouldBe notificationToken

    hyperStorageContent(Db.usersItemPath("user-1")) shouldBe (Text("user-1"), 1)
  }

  "PushNotificationsService" should "delete device token on TokenDelete" in {
    _hyperbus.ask(TokenPut("test-token-a", new CreateNotificationToken(
      platform = Platforms.IOS,
      appName = "com.test-app",
      deviceToken = "test-device-token-1",
      userId = "user-2"
    ))).runAsync.futureValue shouldBe a[Created[_]]

    hyperStorageContent.get(Db.notificationTokenPath("test-token-a")) shouldBe a[Some[_]]
    hyperStorageContent.get(Db.notificationUserTokensItemPath("user-2", "test-token-a")) shouldBe a[Some[_]]
    hyperStorageContent.get(Db.notificationTokenPlatformTokenPath(Platforms.IOS, "test-device-token-1")) shouldBe a[Some[_]]

    _hyperbus.ask(TokenDelete("test-token-a"))
      .runAsync
      .futureValue shouldBe a[NoContent[_]]

    hyperStorageContent.get(Db.notificationTokenPath("test-token-a")) shouldBe None
    hyperStorageContent.get(Db.notificationUserTokensItemPath("user-2", "test-token-a")) shouldBe None
    hyperStorageContent.get(Db.notificationTokenPlatformTokenPath(Platforms.IOS, "test-device-token-1")) shouldBe None
  }

  "PushNotificationsService" should "fail on delete non-existing device token" in {
    _hyperbus.ask(TokenDelete("test-token-a")).runAsync.failed.futureValue shouldBe a[NotFound[_]]
  }

  "PushNotificationsService" should "delete device token on ApnsBadDeviceTokensFeedPost" in {
    _hyperbus.ask(TokenPut("test-token-a", new CreateNotificationToken(
      platform = Platforms.IOS,
      appName = "com.test-app",
      deviceToken = "test-device-token-1",
      userId = "user-2"
    ))).runAsync.futureValue shouldBe a[Created[_]]

    hyperStorageContent.get(Db.notificationTokenPath("test-token-a")) shouldBe a[Some[_]]
    hyperStorageContent.get(Db.notificationUserTokensItemPath("user-2", "test-token-a")) shouldBe a[Some[_]]
    hyperStorageContent.get(Db.notificationTokenPlatformTokenPath(Platforms.IOS, "test-device-token-1")) shouldBe a[Some[_]]

    _hyperbus.publish(ApnsBadDeviceTokensFeedPost(BadToken(deviceToken = "test-device-token-1", bundleId = "com.test-app")))
      .runAsync
      .futureValue

    eventually {
      hyperStorageContent.get(Db.notificationTokenPath("test-token-a")) shouldBe None
      hyperStorageContent.get(Db.notificationUserTokensItemPath("user-2", "test-token-a")) shouldBe None
      hyperStorageContent.get(Db.notificationTokenPlatformTokenPath(Platforms.IOS, "test-device-token-1")) shouldBe None
    }
  }

  // TODO: HyperStorageMock does not support collections and views
//  "PushNotificationsService" should "send push notification to user" in {
//    // val apnsServiceMock = new ApnsServiceMock(apnsPostHandler = { implicit r => Task.now(Ok(EmptyBody)) })
//    // val subscriptions = _hyperbus.subscribe(apnsServiceMock, apnsServiceMock.serviceLogger)
//
//    _hyperbus.ask(TokenPut("test-token-a", new CreateNotificationToken(
//      platform = Platforms.IOS,
//      appName = "com.test-app",
//      deviceToken = "test-device-token-1",
//      userId = "user-2"
//    ))).runAsync.futureValue shouldBe a[Created[_]]
//
//    val x = _hyperbus.ask(UserNotificationsPost("user-2", new PushNotification(Notification("test-title", "test-body"))))
//      .runAsync
//      .futureValue
//
//    // subscriptions.foreach(_.cancel())
//  }
}
