package com.hypertino.services.pushnotifications

import com.hypertino.binders.value.{Lst, Obj}
import com.hypertino.services.pushnotifications.api.{Notification, PushNotification}
import org.scalatest.{FlatSpec, Matchers}

class PayloadBuilderTest extends FlatSpec with Matchers {
  "PayloadBuilder" should "build correct apns message without badge" in {
    val notification = Notification("title", "body")

    val pushNotification = PushNotification(notification)

    val payload = PayloadBuilder.buildApnsPayload(pushNotification)

    payload shouldBe """{"aps":{"alert":{"body":"body","title":"title"}}}"""
  }

  "PayloadBuilder" should "build correct apns message with badge" in {
    val notification = Notification("title", "body", Some(10))

    val pushNotification = PushNotification(notification)

    val payload = PayloadBuilder.buildApnsPayload(pushNotification)

    payload shouldBe """{"aps":{"badge":10,"alert":{"body":"body","title":"title"}}}"""
  }

  "PayloadBuilder" should "build correct apns message with badge and data" in {
    val notification = Notification("title", "body", Some(10))

    val data = Obj.from("text" -> "txt",
      "long" -> 11l,
      "double" -> 11.1d,
      "obj" -> Obj.from("text2" -> "txt2"),
      "lst" -> Lst(Seq("text3", Obj.from("embedded" -> "_obj_"))))

    val pushNotification = PushNotification(notification, data)

    val payload = PayloadBuilder.buildApnsPayload(pushNotification)

    payload shouldBe """{"aps":{"badge":10,"alert":{"body":"body","title":"title"}},"double":11.1,"obj":{"text2":"txt2"},"text":"txt","lst":["text3",{"embedded":"_obj_"}],"long":11}"""
  }
}
