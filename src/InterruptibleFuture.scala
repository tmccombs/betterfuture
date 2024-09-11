package betterfuture

import java.util.concurrent.atomic.AtomicBoolean
import scala.concurrent.{Future, ExecutionContext, Promise}

/** A Future that is allowed to be interrupted.
  *
  * It acts a cancellable future, but the body of the future can itself handle
  * getting an interrupt on the thread it is running on.
  */
class InterruptibleFuture[+T] private (body: => Future[T])(using
    ec: ExecutionContext,
    c: Cancellable
) extends WrappedFuture[T] {

  private var runningThread: Thread = null
  private val promise = Promise[T]()

  protected val wrappedFuture = promise.future

  promise.completeWith(Future.delegate {
    c.cancelledException.fold(run())(Future.failed)
  })
  c.onCancelWith { t =>
    promise.tryFailure(t)
    tryInterrupt()
  }(using ExecutionContext.parasitic)

  /** Try to interrupt the thread currently running the future.
    *
    * If the future is not currently being run, nothing happens.
    *
    * @return
    *   true if the thread was interrupted, false otherwise
    */
  def tryInterrupt(): Boolean = synchronized {
    if (runningThread != null) {
      runningThread.interrupt()
      true
    } else {
      false
    }
  }

  // should we have a way to interrupt if the future hasn't started yet, by storing in a boolean?

  private def run(): Future[T] = {
    synchronized { runningThread = Thread.currentThread() }
    try {
      body
    } finally {
      synchronized { runningThread = null }
    }
  }

}

object InterruptibleFuture {
  def apply[T](
      body: => T
  )(using ec: ExecutionContext, c: Cancellable): InterruptibleFuture[T] =
    new InterruptibleFuture(Future.successful(body))

  def delegate[T](body: => Future[T])(using ExecutionContext)(using
      c: Cancellable
  ): Future[T] = new InterruptibleFuture(body)
}
