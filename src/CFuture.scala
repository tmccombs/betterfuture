package betterfuture

import java.util.concurrent.atomic.AtomicReference
import scala.concurrent.{Future, ExecutionContext, CanAwait}
import scala.concurrent.duration.Duration
import scala.util.Try
import TaskVal.Context

/** An extension of Future that keeps track of a set of `TaskVal`s throughout
  * the execution.
  *
  * If a value of a TaskVal is updated in the current context by the future,
  * then that is propagated to futures further down stream (for example with
  * map, flatMap, etc).
  */
class CFuture[+T] private (
    inner: Future[T],
    private val context: CFuture.ContextCell
) extends Future[T] {

  import CFuture.ContextCell

  override def onComplete[U](f: Try[T] => U)(using ExecutionContext): Unit =
    inner.onComplete { t =>
      context.context(f(t))
    }

  override def isCompleted: Boolean = inner.isCompleted

  override def value: Option[Try[T]] = inner.value

  override def ready(atMost: Duration)(using CanAwait) = {
    inner.ready(atMost)
    this
  }

  override def result(atMost: Duration)(using CanAwait): T =
    inner.result(atMost)

  override def transform[S](
      f: Try[T] => Try[S]
  )(using ExecutionContext): CFuture[S] = {
    val newContext = context.clone()
    new CFuture(
      inner.transform[S] { t =>
        contextRun(newContext)(f(t))
      },
      newContext
    )
  }

  override def transformWith[S](
      f: Try[T] => Future[S]
  )(using ExecutionContext): CFuture[S] = {
    val newContext = context.clone()
    new CFuture(
      inner.transformWith[S] { t =>
        contextRun(newContext)(f(t))
      },
      newContext
    )
  }

  // We have to override this, because the normal implementation calls `f`
  // in `that`. If possible, we should merge the contexts together.
  /** Combine this Future with another Future.
    *
    * If the other future is also a [[CFuture]], then merge the context for this
    * future with the contex of `that`. The context of `that` has higher
    * precedence.
    */
  override def zipWith[U, R](that: Future[U])(f: (T, U) => R)(using
      ExecutionContext
  ): Future[R] = {
    val newContext = context.clone()
    new CFuture(
      super.zipWith(that) { (t, u) =>
        val c = that match {
          case cf: CFuture[U] =>
            context.context ++ cf.context.context
          case _ => context.context
        }

        c(newContext.withUpdate(f(t, u)))
      },
      newContext
    )
  }

  private def contextRun[T](newContext: ContextCell)(f: => T): T =
    context.context(newContext.withUpdate(f))
}

object CFuture {
  private[CFuture] class ContextCell(
      @volatile
      var context: Context
  ) extends Cloneable {
    override def clone(): ContextCell = ContextCell(context)

    inline def withUpdate[T](f: => T): T = try {
      f
    } finally {
      context = TaskVal.currentContext
    }
  }

  opaque type WithContext = ContextCell

  extension (c: WithContext) {
    def apply[T](body: => T)(using ExecutionContext): CFuture[T] =
      new CFuture(Future(c.context(c.withUpdate(body))), c)

    def delegate[T](body: => Future[T])(using ExecutionContext): CFuture[T] =
      new CFuture(Future.delegate(c.context(c.withUpdate(body))), c)

    def unit: CFuture[Unit] = new CFuture(Future.unit, c)

  }

  def apply[T](body: => T)(using ExecutionContext): CFuture[T] =
    withContext(TaskVal.currentContext)(body)

  /** Allow creating [[CFuture]]s with a specific context.
    */
  def withContext(context: Context): WithContext = ContextCell(context)

  /** Convenience method for Creating a [[CFuture]] initialized with a single
    * TaskVal
    */
  def withTaskVal[T](key: TaskVal[T], value: T): WithContext = withContext(
    Context.empty.withValue(key, value)
  )

  def delegate[T](body: => Future[T])(using ExecutionContext): CFuture[T] =
    withContext(TaskVal.currentContext).delegate(body)

  def unit: CFuture[Unit] = withContext(TaskVal.currentContext).unit

  /** Extension methods on [[Future]] to add context to future transformations.
    */
  object syntax {

    extension [T](f: Future[T]) {

      /** Set the supplied context as the initial context for all future
        * transformations on the returned [[scala.concurrent.Future]].
        *
        * Note that this does not preserve any current values of [[TaskVal]]s in
        * future, if this is a [[CFuture]].
        */
      def withContext(c: TaskVal.Context): CFuture[T] =
        new CFuture(f, ContextCell(c))

      /** Use the current value of the [[TaskVal]] context for all
        * transformations on the returned [[scala.concurrent.Future]].
        */
      def withCurrentContext: CFuture[T] = withContext(TaskVal.currentContext)

      /** Make a new future for which all transformations will have the
        * [[TaskVal]] for `key` set to `value`. This will be combined with
        * existing context if the future is a [[CFuture]].
        */
      def withTaskVal[V](key: TaskVal[V], value: V): CFuture[T] = f match {
        case cf: CFuture[T] =>
          cf.transform { t =>
            key.set(value)
            t
          }(using ExecutionContext.parasitic)
        case _ =>
          new CFuture(f, ContextCell(Context.empty.withValue(key, value)))
      }

      def withTaskVals(context: TaskVal.Context): CFuture[T] = f match {
        case cf: CFuture[T] =>
          cf.transform { t =>
            TaskVal.updateAll(context)
            t
          }(using ExecutionContext.parasitic)
        case _ => new CFuture(f, ContextCell(context))
      }
    }

  }
}
