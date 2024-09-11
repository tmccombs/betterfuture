package betterfuture

import scala.concurrent.ExecutionContext
import java.util.concurrent.CountDownLatch

class InterruptibleFutureSpec extends UnitSpec {
  import ExecutionContext.Implicits.global

  "InterruptibleFuture" should "not run if cancelled" in withCancel {
    given ExecutionContext = neverExecutionContext
    val fut = InterruptibleFuture(fail())
    Cancellable.cancel()
    fut must beCanceled
  }

  it should "interrupt running future" in withCancel {
    val latch = new CountDownLatch(1)
    @volatile
    var wasInterrupted = false
    val fut = InterruptibleFuture {
      latch.countDown()
      // wait for interrupt
      try {
        Thread.sleep(Long.MaxValue)
        fail("wasn't interrupted")
      } catch {
        case _: InterruptedException =>
          wasInterrupted = true
      }
    }
    latch.await()
    Cancellable.cancel()
    fut must beCanceled
    wasInterrupted must be(true)
  }

}
