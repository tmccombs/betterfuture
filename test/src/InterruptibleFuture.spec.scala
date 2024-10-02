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
    val latch2 = new CountDownLatch(1)
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
          latch2.countDown()
        case e =>
          fail(e)
          latch2.countDown()
      }
    }
    latch.await()
    Cancellable.cancel()
    fut must beCanceled
    latch2.await()
    wasInterrupted must be(true)
  }

}
