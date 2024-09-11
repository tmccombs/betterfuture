package betterfuture

import scala.concurrent.{Promise, ExecutionContext, Future}
import scala.util.Failure
import java.util.concurrent.CancellationException

/** A context object that allows one or more related tasks to be cancelled.
  *
  * It allows checking if a cancellation has happened, and registering actions
  * to take if a cancellation happens. As well as actually cancelling.
  *
  * There is no garantee on the order cancellation actions are performed in.
  */
class Cancellable private (promise: Promise[Nothing]) {

  /** Cancel everything tied to this canceller.
    *
    * The cancellation can happen asynchronously, so the cancellation may not
    * have been fully performed before this function returns.
    *
    * The associated proise will be a
    *
    * Cancelling an already cancelled Cancellable does nothing.
    */
  def cancel(): Unit = promise.tryFailure(new CancellationException)

  /** Cancel everything using a specific exception.
    *
    * Not that if the canceller has already been cancelled, the
    */
  def cancel(t: Throwable): Unit = promise.tryFailure(t)

  /** @return
    *   true if the Cancellable has been cancelled
    */
  def isCancelled: Boolean = promise.isCompleted

  def cancelledException: Option[Throwable] = promise.future.value match {
    case Some(Failure(e)) => Some(e)
    case _                => None
  }

  /** Register an action to perform when a cancel action happens.
    *
    * If the Cancellable is already cancelled, then run the action as soon as
    * possibly, asynchronously.
    *
    * There is no garantee on the order cancellation actions are performed in.
    */
  def onCancel(action: => Unit)(using ExecutionContext): Unit =
    promise.future.onComplete(_ => action)

  def onCancelWith(action: Throwable => Unit)(using ExecutionContext): Unit =
    promise.future.onComplete {
      case Failure(e) => action(e)
      case _          =>
    }

  /** If this Cancellable is cancelled, then try to resolve the given promise
    * with a failure of the exception this was cancelled with.
    */
  def includePromise(p: Promise[?]): Unit =
    onCancelWith(t => p.tryFailure(t))(using ExecutionContext.parasitic)

  def includeJavaFuture(f: java.util.concurrent.Future[?]): Unit =
    onCancel(f.cancel(false))(using ExecutionContext.parasitic)

  /** Return a Future that will be completed with a failure when a cancel
    * happens.
    *
    * The exception in the Future will either be a `CancellationException` if
    * `cancel` was called, or the exception passed to `cancel`.
    */
  def future: Future[Nothing] = promise.future

  /** Return a new Cancellable that will automatically be cancelld if this
    * Cancellable is cancelled.
    */
  def child(): Cancellable = {
    val promise = Promise[Nothing]()
    includePromise(promise)
    new Cancellable(promise)
  }

  /** If the Cancellable has been cancelled, throw the exception it was
    * cancelled with.
    */
  def throwIfCancelled(): Unit = {
    cancelledException.foreach { e =>
      throw e
    }
  }

  /** Create a wrapped ExecutionContext that will stop executing tasks when
    * Cancellable has been cancelled.
    *
    * Use with caution. Any Futures scheduled after cancellation will never
    * complete.
    */
  def executionContext(wrapped: ExecutionContext): ExecutionContext =
    new ExecutionContext {
      override def execute(runnable: Runnable): Unit = wrapped.execute(() => {
        cancelledException.fold(runnable.run())(reportFailure)
      })

      override def reportFailure(cause: Throwable): Unit =
        wrapped.reportFailure(cause)
    }
}

object Cancellable {

  /** Create a new canceller
    */
  def apply(): Cancellable = new Cancellable(Promise())

  /** Create a new canceller that is already cancelled.
    */
  def cancelled(): Cancellable = new Cancellable(
    Promise.failed(new CancellationException)
  )

  /** Create a new canceler that is already cancelled with a specific exception.
    */
  def cancelled(t: Throwable): Cancellable = new Cancellable(Promise.failed(t))

  /** Cancel the current Cancellable context in scope
    */
  def cancel()(using c: Cancellable): Unit = c.cancel()

  /** Cancel the current Cancellable context in scope
    */
  def cancel(t: Throwable)(using c: Cancellable): Unit = c.cancel(t)

  /** Create a wrapped ExecutionContext that will stop executing tasks when
    * Cancellable has been cancelled.
    *
    * Use with caution. Any Futures scheduled after cancellation will never
    * complete.
    */
  def wrapExecutionContext(ec: ExecutionContext)(using
      c: Cancellable
  ): ExecutionContext = c.executionContext(ec)
}
