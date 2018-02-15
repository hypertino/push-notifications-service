package com.hypertino.services.pushnotifications

import com.hypertino.binders.value._
import com.hypertino.hyperbus.Hyperbus
import com.hypertino.hyperbus.model.{Accepted, Created, DynamicBody, EmptyBody, Headers, MessagingContext, NoContent, NotFound, Ok, PreconditionFailed, ResponseBase, ResponseHeaders}
import com.hypertino.hyperbus.serialization.SerializationOptions
import com.hypertino.hyperbus.subscribe.Subscribable
import com.hypertino.hyperbus.subscribe.annotations.groupName
import com.hypertino.service.control.api.Service
import com.hypertino.services.apns.api.{ApnsBadDeviceTokensFeedPost, ApnsNotification, ApnsPost}
import com.hypertino.services.hyperstorage.api._
import com.hypertino.services.pushnotifications.TaskExtensions._
import com.hypertino.services.pushnotifications.api._
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
    val notificationToken = NotificationToken(r.tokenId,
      r.body.platform,
      r.body.appName,
      r.body.deviceToken,
      r.body.userId,
      System.currentTimeMillis())

    createDeviceToken(notificationToken)
      .flatMap { _ =>
        hyperbus.ask(ContentPut(Db.notificationTokenPath(notificationToken.tokenId), DynamicBody(notificationToken.toValue)))
          .flatMap { response =>
            hyperbus.ask(ContentPut(Db.usersItemPath(notificationToken.userId), DynamicBody(Obj.from("user_id" -> notificationToken.userId)))).flatMap { _ =>
              // TODO: limit amount of user tokens to 5 or 10
              hyperbus.ask(ContentPut(Db.notificationUserTokensItemPath(notificationToken.userId, notificationToken.tokenId), DynamicBody(notificationToken.toValue))).map { _ =>
                response match {
                  case _: Created[_] => Created(EmptyBody)
                  case _: Ok[_] => Ok(EmptyBody)
                }
              }
            }
          }
      }
  }

  def onTokenDelete(implicit r: TokenDelete): Task[ResponseBase] =  removeToken(r.tokenId).map { _ => NoContent(EmptyBody) }

  def onNotificationsPost(implicit r: NotificationsPost): Task[Accepted[EmptyBody]] = {
    hyperbus.ask(ContentGet(Db.usersPath(), perPage = Some(Int.MaxValue))).flatMap { case Ok(usersBody: DynamicBody, _)=>
      val users = usersBody.content.toList.map(_.toMap).map(_("user_id").toString())

      Task.gatherUnordered(users.map { userId =>
        // TODO: payload build is called many times on this push notification
        hyperbus.ask(UserNotificationsPost(userId, r.body)).onErrorHandle { t =>
          logger.warn(s"Failed to send push notifications to user '$userId'", t)
          Unit
        }
      })
    }.map { _ =>
      Accepted(EmptyBody)
    }.onErrorRecover { case NotFound(_) => Accepted(EmptyBody) }
  }

  def onUserNotificationsPost(implicit r: UserNotificationsPost): Task[Accepted[EmptyBody]] = {
    // take 5 last tokens
    hyperbus.ask(ContentGet(Db.notificationUserTokensPath(r.userId),
      filter = Some("platform = 'ios'"),
      perPage = Some(5),
      sortBy = Some("-created_at"))).flatMap { case Ok(tokensBody: DynamicBody, headers: ResponseHeaders) =>
        val tokens = tokensBody.content.toList.map(_.toMap)

        logger.trace(s"User '${r.userId}' has at least ${tokens.size} device tokens")

        if (tokens.isEmpty) {
          Task.unit
        }else {
          val payload = PayloadBuilder.buildApnsPayload(r.body)
          Task.gatherUnordered(tokens.map { token =>
            val appName = token("app_name").toString()
            val deviceToken = token("device_token").toString()

            hyperbus.ask(ApnsPost(ApnsNotification(deviceToken, appName, payload)))
          })
        }
      }.map { _ =>
        Accepted(EmptyBody)
      }.onErrorRecover { case NotFound(_) => Accepted(EmptyBody) }
  }

  @groupName("push-notifications-service")
  def onApnsBadDeviceTokensFeedPost(implicit r: ApnsBadDeviceTokensFeedPost): Future[Ack] =
    removeExistingDeviceToken(Db.notificationTokenPlatformTokenPath(Platforms.IOS, r.body.deviceToken))
      .map(_ => Continue)
      .runAsync

  override def stopService(controlBreak: Boolean, timeout: FiniteDuration): Future[Unit] = Future {
    handlers.foreach(_.cancel())
    logger.info(s"${getClass.getName} is STOPPED")
  }

  private def createDeviceToken(notificationToken: NotificationToken)(implicit mcx: MessagingContext): Task[ResponseBase] = {
    val deviceTokenPath = Db.notificationTokenPlatformTokenPath(notificationToken.platform, notificationToken.deviceToken)

    hyperbus.ask(ContentPut(deviceTokenPath, DynamicBody(notificationToken.toValue),
      headers = Headers(HyperStorageHeader.IF_NONE_MATCH → "*"))) .retryWhen { case _: PreconditionFailed[_] =>
        removeExistingDeviceToken(deviceTokenPath)
      }
  }

  private def removeExistingDeviceToken(deviceTokenPath: String)(implicit mcx: MessagingContext):Task[Any] =
    hyperbus.ask(ContentGet(deviceTokenPath))
      .map { _.body.content.toMap("token_id").toString() }
      .flatMap { existingTokenId =>
        removeToken(existingTokenId)
      }

  private def removeToken(tokenId: String)(implicit mcx: MessagingContext): Task[Any] ={
    val existingTokenPath = Db.notificationTokenPath(tokenId)

    // TODO: check doc's revision
    hyperbus.ask(ContentGet(existingTokenPath))
      .map { _.body.content.toMap }
      .flatMap { notificationToken =>
        val userId = notificationToken("user_id").toString()
        val platform = notificationToken("platform").toString()
        val deviceToken = notificationToken("device_token").toString()

        val deviceTokenPath = Db.notificationTokenPlatformTokenPath(platform, deviceToken)

        Task.zip3(
          hyperbus.ask(ContentDelete(deviceTokenPath)),
          deleteIfExists(Db.notificationUserTokensItemPath(userId, tokenId)),
          deleteIfExists(existingTokenPath)
        )
      }
  }

  private def deleteIfExists(path: String)(implicit mcx: MessagingContext): Task[Boolean] =
    hyperbus.ask(ContentDelete(path))
      .map { _ => true }
      .onErrorRecover { case _: NotFound[_] => false }
}
