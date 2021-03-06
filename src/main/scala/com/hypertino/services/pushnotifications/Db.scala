package com.hypertino.services.pushnotifications

object Db {
  def usersPath(): String = s"push-notifications-service/users~"
  def usersItemPath(userId: String): String = s"push-notifications-service/users~/$userId"
  def notificationTokenPath(tokenId: String): String = s"push-notifications-service/tokens/$tokenId"
  def notificationUserTokensPath(userId: String): String = s"push-notifications-service/users/$userId/tokens~"
  def notificationUserTokensItemPath(userId: String, tokenId: String): String = s"push-notifications-service/users/$userId/tokens~/$tokenId"
  def notificationTokenPlatformTokenPath(platform: String, deviceToken: String): String = s"push-notifications-service/platforms/$platform/device-tokens/$deviceToken"
}
