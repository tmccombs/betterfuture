package betterfuture

import java.util.concurrent.CancellationException
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.BeMatcher
import org.scalatest.matchers.must.Matchers
import org.scalatest.matchers.{Matcher, MatchResult}
import scala.concurrent.{ExecutionContext, Future}
import scala.util.Failure

abstract class UnitSpec
    extends AnyFlatSpec
    with Matchers
    with org.scalatest.concurrent.ScalaFutures {

  def withCancel[T](body: Cancellable ?=> T): T = body(using Cancellable())

  /** Execution context that never actually runs anything
    */
  val neverExecutionContext = new ExecutionContext {
    def execute(_r: Runnable): Unit = ()
    def reportFailure(_t: Throwable): Unit = ()
  }

  val beCanceled =
    be(a[CancellationException]).compose((f: Future[?]) => f.failed.futureValue)
}
