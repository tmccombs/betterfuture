package betterfuture

import scala.concurrent.{Future, ExecutionContext, CanAwait}
import scala.concurrent.duration.Duration
import scala.util.Try

/** A class that removes a lot of the boilerplate for implementing a Future that
  * wraps another Future.
  */
trait WrappedFuture[+T] extends Future[T] {
  protected def wrappedFuture: Future[T]

  override def onComplete[U](f: Try[T] => U)(using ExecutionContext): Unit =
    wrappedFuture.onComplete(f)

  override def isCompleted: Boolean = wrappedFuture.isCompleted

  override def value: Option[Try[T]] = wrappedFuture.value

  override def transform[S](f: Try[T] => Try[S])(using
      ExecutionContext
  ): Future[S] = wrappedFuture.transform(f)
  override def transformWith[S](f: Try[T] => Future[S])(using
      ExecutionContext
  ): Future[S] = wrappedFuture.transformWith(f)

  override def ready(atMost: Duration)(using CanAwait) = {
    wrappedFuture.ready(atMost)
    this
  }

  override def result(atMost: Duration)(using CanAwait): T =
    wrappedFuture.result(atMost)
}
