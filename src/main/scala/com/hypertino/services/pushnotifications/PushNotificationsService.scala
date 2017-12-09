package com.hypertino.services.pushnotifications

import com.hypertino.binders.value._
import com.hypertino.hyperbus.Hyperbus
import com.hypertino.hyperbus.model.{Accepted, Created, DynamicBody, EmptyBody, Headers, MessagingContext, NoContent, NotFound, Ok, PreconditionFailed, ResponseBase, ResponseHeaders}
import com.hypertino.hyperbus.serialization.SerializationOptions
import com.hypertino.hyperbus.subscribe.Subscribable
import com.hypertino.hyperbus.subscribe.annotations.groupName
import com.hypertino.service.control.api.Service
import com.hypertino.services.apns.api.{ApnsBadDeviceTokensFeedPost, ApnsNotification, ApnsPost}
import com.hypertino.services.hyperstorage.api.{ContentDelete, ContentGet, ContentPut, HyperStorageHeader}
import com.hypertino.services.pushnotifications.api._
import com.hypertino.services.pushnotifications.TaskExtensions._
import com.typesafe.scalalogging.StrictLogging
import monix.eval.Task
import monix.execution.Ack.Continue
import monix.execution.{Ack, Scheduler}
import scaldi.{Injectable, Injector}

import scala.concurrent.Future
import scala.concurrent.duration.FiniteDuration

class PushNotificationsService(implicit val injector: Injector) extends Service with Injectable with Subscribable with StrictLogging {
  implicit val so = SerializationOptions.default
  import so._

  private implicit val scheduler: Scheduler = inject[Scheduler]
  private val hyperbus = inject[Hyperbus]

  private val handlers = hyperbus.subscribe(this, logger)

  logger.info(s"${getClass.getName} is STARTED")

  def onTokenPut(implicit r: TokenPut): Task[ResponseBase] = {
    val tokenIdFk = TokenId(r.tokenId)

    val deviceTokenPath = Db.notificationTokenPlatformTokenPath(r.body.platform, r.body.token)

    createDeviceToken(tokenIdFk, deviceTokenPath)
      .flatMap { _ =>
        val notificationToken = NotificationToken(r.tokenId,
          r.body.platform,
          r.body.appName,
          r.body.token,
          r.body.userId,
          System.currentTimeMillis())

        hyperbus.ask(ContentPut(Db.notificationTokenPath(notificationToken.tokenId), DynamicBody(notificationToken.toValue)))
          .flatMap { response =>
            hyperbus.ask(ContentPut(Db.notificationUserTokensItemPath(notificationToken.userId, notificationToken.tokenId), DynamicBody(tokenIdFk.toValue))).map { _ =>
               response match {
                case _: Created[_] => Created(EmptyBody)
                case _: Ok[_] => Ok(EmptyBody)
              }
            }
          }
      }
  }

  def onTokenDelete(implicit r: TokenDelete): Task[ResponseBase] =  removeToken(r.tokenId).map { _ => NoContent(EmptyBody) }

  def onNotificationsPost(implicit r: NotificationsPost): Task[Accepted[EmptyBody]] = {
    // TODO: paging, other platforms
    hyperbus.ask(ContentGet(Db.notificationTokensPath(), filter = Some("platform = 'ios'"), perPage = Some(Int.MaxValue))).flatMap { case Ok(tokensBody: DynamicBody, headers: ResponseHeaders) =>
      val tokens = tokensBody.content.toList.map(_.to[NotificationToken])

      if (tokens.isEmpty) {
        Task.unit
      }else {
        val payload = PayloadBuilder.buildApnsPayload(r.body)

        Task.gatherUnordered(tokens.map { token =>
          // publishing regular request
          hyperbus.publish(ApnsPost(ApnsNotification(token.token, token.appName, payload)))
        })
      }
    }.map { _ =>
      Accepted(EmptyBody)
    }
  }

  @groupName("push-notifications-service")
  def onApnsBadDeviceTokensFeedPost(implicit r: ApnsBadDeviceTokensFeedPost): Future[Ack] =
    removeExistingDeviceToken(Db.notificationTokenPlatformTokenPath("ios", r.body.deviceToken))
      .map(_ => Continue).runAsync

  override def stopService(controlBreak: Boolean, timeout: FiniteDuration): Future[Unit] = Future {
    handlers.foreach(_.cancel())
    logger.info(s"${getClass.getName} is STOPPED")
  }

  private def createDeviceToken(tokenId: TokenId, deviceTokenPath: String)(implicit mcx: MessagingContext): Task[ResponseBase] = Task.defer {
    hyperbus.ask(ContentPut(deviceTokenPath, DynamicBody(tokenId.toValue), headers = Headers(HyperStorageHeader.IF_NONE_MATCH → "*")))
  }.retryWhen { case _: PreconditionFailed[_] => removeExistingDeviceToken(deviceTokenPath) }

  private def removeExistingDeviceToken(deviceTokenPath: String)(implicit mcx: MessagingContext):Task[Any] =
    hyperbus.ask(ContentGet(deviceTokenPath))
      .map { _.body.content.to[TokenId].tokenId }
      .flatMap { existingTokenId =>
        removeToken(existingTokenId)
      }

  private def removeToken(tokenId: String)(implicit mcx: MessagingContext): Task[Any] ={
    val existingTokenPath = Db.notificationTokenPath(tokenId)

    // TODO: check doc's revision
    hyperbus.ask(ContentGet(existingTokenPath))
      .map { _.body.content.to[NotificationToken] }
      .flatMap { notificationToken =>
        val deviceTokenPath = Db.notificationTokenPlatformTokenPath(notificationToken.platform, notificationToken.token)

        Task.zip3(
          hyperbus.ask(ContentDelete(deviceTokenPath)),
          deleteUserTokenIfExists(Db.notificationUserTokensItemPath(notificationToken.userId, tokenId)),
          deleteExistingTokenIfExists(existingTokenPath)
        )
      }
  }

  private def deleteUserTokenIfExists(userTokenPath: String)(implicit mcx: MessagingContext): Task[Boolean] =
    hyperbus.ask(ContentDelete(userTokenPath))
      .map { _ => true }
      .onErrorRecover { case _: NotFound[_] => false }

  private def deleteExistingTokenIfExists(existingTokenPath: String)(implicit mcx: MessagingContext): Task[Boolean] =
    hyperbus.ask(ContentDelete(existingTokenPath))
      .map { _ => true }
      .onErrorRecover { case _: NotFound[_] => false }
}
