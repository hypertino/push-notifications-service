package com.hypertino.services.pushnotifications

import java.util

import com.hypertino.binders.value.{Bool, Lst, Number, Obj, Text, ValueVisitor}
import com.hypertino.services.pushnotifications.api.PushNotification
import com.turo.pushy.apns.util.ApnsPayloadBuilder

object PayloadBuilder {
  private val toPayloadVisitor = new ValueVisitor[Any] {
    override def visitBool(d: Bool): Boolean = d.v

    override def visitText(d: Text): String = d.v

    override def visitObj(d: Obj): util.HashMap[String, Any] = {
      val hashMap = new util.HashMap[String, Any]()
      d.v.foreach{ case (k, v) => hashMap.put(k, v ~~ this) }
      hashMap
    }

    override def visitNumber(d: Number): Any = {
      if(d.v.isWhole()){
        d.v.toLong
      }else{
        d.v.toDouble
      }
    }
    override def visitLst(d: Lst): Array[Any] = d.toSeq.map { _ ~~ this }.toArray

    override def visitNull(): Any = null
  }

  def buildApnsPayload(pushNotification: PushNotification): String = {
    val builder = new ApnsPayloadBuilder()
      .setAlertTitle(pushNotification.notification.title)
      .setAlertBody(pushNotification.notification.body)

    pushNotification.data.toMap.map { case (k, v) =>
      builder.addCustomProperty(k, v ~~ toPayloadVisitor)
    }

    builder.buildWithDefaultMaximumLength()
  }
}
