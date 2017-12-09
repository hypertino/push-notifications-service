package com.hypertino.services.pushnotifications

import monix.eval.Task

object TaskExtensions {
  implicit class RetryWhen[T](val source: Task[T]) extends AnyVal {
    def retryWhen(handler: PartialFunction[Throwable, Task[Any]]): Task[T] =
      source.onErrorHandleWith(e => handler.applyOrElse(e, Task.raiseError).flatMap{ _ =>
        source.retryWhen(handler)
      })
  }
}