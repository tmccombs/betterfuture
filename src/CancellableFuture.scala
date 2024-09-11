package betterfuture

import scala.concurrent.{Future, Promise, Batchable, ExecutionContext}
import scala.util.Failure
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.{CancellationException}
import scala.concurrent.duration.Duration
import scala.concurrent.CanAwait
import scala.collection.immutable.Iterable

object CancellableFuture {

  def apply[T](
      body: => T
  )(using ec: ExecutionContext, c: Cancellable): Future[T] = delegate(
    Future.successful(body)
  )

  def delegate[T](
      body: => Future[T]
  )(using ec: ExecutionContext, c: Cancellable): Future[T] = {
    val promise = Promise[T]()
    c.includePromise(promise)

    promise.completeWith(Future.delegate {
      c.cancelledException.fold(body)(Future.failed)
    })

    promise.future
  }

  def foldLeft[T, R](futures: Iterable[Future[T]])(
      zero: R
  )(op: (R, T) => R)(using ec: ExecutionContext, c: Cancellable): Future[R] =
    Future.foldLeft(futures)(zero) { (r, t) =>
      c.throwIfCancelled()
      op(r, t)
    }

  def reduceLeft[T, R >: T](
      futures: Iterable[Future[T]]
  )(op: (R, T) => R)(using ec: ExecutionContext, c: Cancellable): Future[R] =
    Future.reduceLeft(futures) { (r, t) =>
      c.throwIfCancelled()
      op(r, t)
    }
}
