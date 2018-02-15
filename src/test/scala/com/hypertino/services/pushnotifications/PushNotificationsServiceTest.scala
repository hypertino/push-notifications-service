package com.hypertino.services.pushnotifications

import com.hypertino.binders.value.Obj
import com.hypertino.hyperbus.model.{Accepted, Created, EmptyBody, NoContent, NotFound, Ok}
import com.hypertino.hyperstorage.api.ContentGet
import com.hypertino.services.apns.api.{ApnsBadDeviceTokensFeedPost, BadToken}
import com.hypertino.services.pushnotifications.api._
import monix.eval.Task
import org.scalamock.scalatest.MockFactory
import org.scalatest.concurrent.{Eventually, ScalaFutures}
import org.scalatest.{BeforeAndAfter, FlatSpec, Matchers}

class PushNotificationsServiceTest extends FlatSpec
  with TestServiceBase
  with MockFactory {

  start(
    services = Seq(
      new PushNotificationsService()
    )
  )
  import _hyperStorageMock._
  import so._

  val serviceMockX: ServiceMock = new ServiceMock(_hyperbus)

  override def beforeEach(): Unit = {
    super.beforeEach()
    serviceMockX.reset()
  }

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

    _hyperbus.ask(ContentGet(Db.notificationTokenPlatformTokenPath(Platforms.IOS, "test-device-token-1")))
      .map(_.body.content)
      .runAsync
      .futureValue shouldBe notificationTokenValue

    _hyperbus.ask(ContentGet(Db.notificationUserTokensItemPath("user-1", "test-token-a")))
      .map(_.body.content)
      .runAsync
      .futureValue shouldBe notificationTokenValue

    _hyperbus.ask(ContentGet(Db.usersItemPath("user-1")))
      .map(_.body.content)
      .runAsync
      .futureValue shouldBe Obj.from("user_id" -> "user-1")
  }

  it should "delete device token on TokenDelete" in {
    _hyperbus.ask(TokenPut("test-token-a", new CreateNotificationToken(
      platform = Platforms.IOS,
      appName = "com.test-app",
      deviceToken = "test-device-token-1",
      userId = "user-2"
    ))).runAsync.futureValue shouldBe a[Created[_]]

    _hyperbus.ask(ContentGet(Db.notificationTokenPath("test-token-a"))).runAsync.futureValue shouldBe a[Ok[_]]
    _hyperbus.ask(ContentGet(Db.notificationUserTokensItemPath("user-2", "test-token-a"))).runAsync.futureValue shouldBe a[Ok[_]]
    _hyperbus.ask(ContentGet(Db.notificationTokenPlatformTokenPath(Platforms.IOS, "test-device-token-1"))).runAsync.futureValue shouldBe a[Ok[_]]

    _hyperbus.ask(TokenDelete("test-token-a"))
      .runAsync
      .futureValue shouldBe a[NoContent[_]]

    _hyperbus.ask(ContentGet(Db.notificationTokenPath("test-token-a"))).failed.runAsync.futureValue shouldBe a[NotFound[_]]
    _hyperbus.ask(ContentGet(Db.notificationUserTokensItemPath("user-2", "test-token-a"))).failed.runAsync.futureValue shouldBe a[NotFound[_]]
    _hyperbus.ask(ContentGet(Db.notificationTokenPlatformTokenPath(Platforms.IOS, "test-device-token-1"))).failed.runAsync.futureValue shouldBe a[NotFound[_]]
  }

  it should "fail on delete non-existing device token" in {
    _hyperbus.ask(TokenDelete("test-token-a")).runAsync.failed.futureValue shouldBe a[NotFound[_]]
  }

  it should "delete device token on ApnsBadDeviceTokensFeedPost" in {
    _hyperbus.ask(TokenPut("test-token-a", new CreateNotificationToken(
      platform = Platforms.IOS,
      appName = "com.test-app",
      deviceToken = "test-device-token-1",
      userId = "user-2"
    ))).runAsync.futureValue shouldBe a[Created[_]]

    _hyperbus.ask(ContentGet(Db.notificationTokenPath("test-token-a"))).runAsync.futureValue shouldBe a[Ok[_]]
    _hyperbus.ask(ContentGet(Db.notificationUserTokensItemPath("user-2", "test-token-a"))).runAsync.futureValue shouldBe a[Ok[_]]
    _hyperbus.ask(ContentGet(Db.notificationTokenPlatformTokenPath(Platforms.IOS, "test-device-token-1"))).runAsync.futureValue shouldBe a[Ok[_]]

    _hyperbus.publish(ApnsBadDeviceTokensFeedPost(BadToken(deviceToken = "test-device-token-1", bundleId = "com.test-app")))
      .runAsync
      .futureValue

    eventually {
      _hyperbus.ask(ContentGet(Db.notificationTokenPath("test-token-a"))).failed.runAsync.futureValue shouldBe a[NotFound[_]]
      _hyperbus.ask(ContentGet(Db.notificationUserTokensItemPath("user-2", "test-token-a"))).failed.runAsync.futureValue shouldBe a[NotFound[_]]
      _hyperbus.ask(ContentGet(Db.notificationTokenPlatformTokenPath(Platforms.IOS, "test-device-token-1"))).failed.runAsync.futureValue shouldBe a[NotFound[_]]
    }
  }

  ignore should "send push notification to all devices" in {
    _hyperbus.ask(TokenPut("test-token-a", new CreateNotificationToken(
      platform = Platforms.IOS,
      appName = "com.test-app",
      deviceToken = "test-device-token-1",
      userId = "user-1"
    ))).runAsync.futureValue shouldBe a[Created[_]]

    _hyperbus.ask(TokenPut("test-token-b", new CreateNotificationToken(
      platform = Platforms.IOS,
      appName = "com.test-app",
      deviceToken = "test-device-token-2",
      userId = "user-2"
    ))).runAsync.futureValue shouldBe a[Created[_]]

    _hyperbus.ask(TokenPut("test-token-c", new CreateNotificationToken(
      platform = Platforms.IOS,
      appName = "com.test-app",
      deviceToken = "test-device-token-3",
      userId = "user-2"
    ))).runAsync.futureValue shouldBe a[Created[_]]


    val mockedMethods = mock[MockedMethods]
    serviceMockX.mockHandler = Some(mockedMethods)

    (mockedMethods.onApnsPost _).expects(*).returns(Task(Accepted(EmptyBody))).repeated(3)

    _hyperbus.ask(NotificationsPost(new PushNotification(Notification("title", "subject"))))
      .runAsync
      .futureValue shouldBe a[Accepted[_]]
  }

  ignore should "send push notification to user's devices" in {
    _hyperbus.ask(TokenPut("test-token-a", new CreateNotificationToken(
      platform = Platforms.IOS,
      appName = "com.test-app",
      deviceToken = "test-device-token-1",
      userId = "user-1"
    ))).runAsync.futureValue shouldBe a[Created[_]]

    _hyperbus.ask(TokenPut("test-token-b", new CreateNotificationToken(
      platform = Platforms.IOS,
      appName = "com.test-app",
      deviceToken = "test-device-token-2",
      userId = "user-2"
    ))).runAsync.futureValue shouldBe a[Created[_]]

    _hyperbus.ask(TokenPut("test-token-c", new CreateNotificationToken(
      platform = Platforms.IOS,
      appName = "com.test-app",
      deviceToken = "test-device-token-3",
      userId = "user-2"
    ))).runAsync.futureValue shouldBe a[Created[_]]


    val mockedMethods = mock[MockedMethods]
    serviceMockX.mockHandler = Some(mockedMethods)

    (mockedMethods.onApnsPost _).expects(*).returns(Task(Accepted(EmptyBody))).repeated(2)

    _hyperbus.ask(UserNotificationsPost("user-2", new PushNotification(Notification("title", "subject"))))
      .runAsync
      .futureValue shouldBe a[Accepted[_]]
  }
}
