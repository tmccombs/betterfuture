package betterfuture

import scala.annotation.targetName
import scala.concurrent.ExecutionContext

final class TaskVal[T] {
  import TaskVal.{currentContext, Scope}

  def get: Option[T] = currentContext.get(this)

  def getOrElse(default: T): T = currentContext.getOrElse(this, default)

  def set(v: T): Scope = {
    val old = TaskVal.current.get()
    TaskVal.current.set(old.withValue(this, v))
    Scope(old)
  }

  def runWith[R](v: T)(body: => R): R =
    currentContext.withValue(this, v)(body)
}

object TaskVal {

  class Scope private[TaskVal] (reset: Context) extends AutoCloseable {
    def close(): Unit = {
      current.set(reset)
    }
  }

  class Context private (private val values: Map[TaskVal[?], Any]) {

    def withValue[T](key: TaskVal[T], value: T): Context = new Context(
      values + (key -> value)
    )

    @targetName("merge")
    def ++(other: Context): Context = new Context(values ++ other.values)

    @targetName("add")
    def +[T](pair: (TaskVal[T], T)): Context = new Context(values + pair)

    def get[T](key: TaskVal[T]): Option[T] =
      values.get(key).asInstanceOf[Option[T]]

    def getOrElse[T](key: TaskVal[T], default: => T): T =
      values.getOrElse(key, default).asInstanceOf[T]

    def apply[T](body: => T): T = {
      val old = current.get()
      current.set(this)
      try {
        body
      } finally {
        current.set(old)
      }
    }

    /** Suspend a body into a function that when called will run the body with
      * the current context set to this context.
      */
    def suspend[T](body: => T): () => T = () => apply(body)

    /** Wrap a Runnable in a new Runnable that runs the original with the
      * current context set to this context
      */
    def wrap(r: Runnable): Runnable = new Runnable {
      def run(): Unit = apply(r.run())
    }

    /** Wrap a function in a new function that is called with the current
      * context set to this
      */
    def wrap[T](f: () => T): () => T = () => apply(f())

    /** Wrap an execution context in a new execution context that runs each task
      * using this as the (initial) current context.
      */
    def wrap(ec: ExecutionContext): ExecutionContext = new ExecutionContext {
      def execute(runnable: Runnable): Unit = ec.execute(wrap(runnable))
      def reportFailure(cause: Throwable): Unit = ec.reportFailure(cause)
    }

    /** Replace the current TaskVal context with this context
      */
    def makeCurrent(): Scope = {
      val scope = Scope(current.get())
      current.set(this)
      scope
    }

    override def toString: String = values.mkString("Context(", ",", ")")
  }

  object Context {
    val empty = new Context(Map.empty)

  }

  private[TaskVal] val current =
    ThreadLocal.withInitial[Context](() => Context.empty)

  /** The current (thread local) context of TaskVals
    *
    * This contains all the current values of TaskVals that are set.
    */
  def currentContext: Context = current.get()

  /** Suspend a body into a function that when called will run the body with the
    * value of all TaskVals set to the values they have at the time of calling
    * this method
    */
  def suspend[T](body: => T): () => T = currentContext.suspend(body)

  /** Update the current context with all values from another context.
    *
    * Typically, this context would be built from `Context.empty` with only some
    * TaskVal's set
    */
  def updateAll(context: Context): Scope = {
    val old = current.get()
    current.set(old ++ context)
    Scope(old)
  }

  /** Replace all currently set TaskVals with the TaskVals in another context.
    *
    * Note that this will unset any TaskVals set in the old current context that
    * don't exist in the new context.
    */
  def replaceAll(context: Context): Scope = {
    val scope = Scope(current.get())
    current.set(context)
    scope
  }

  /** Create an execution context that will preserve the TaskVal context from
    * the caller to the tasks executed.
    */
  def executionContext(wrapped: ExecutionContext): ExecutionContext =
    new ExecutionContext {
      def execute(runnable: Runnable): Unit =
        wrapped.execute(currentContext.wrap(runnable))
      def reportFailure(cause: Throwable): Unit = wrapped.reportFailure(cause)
    }
}
