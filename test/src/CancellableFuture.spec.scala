package betterfuture

import scala.util.{Success, Failure}
import scala.concurrent.ExecutionContext
import java.util.concurrent.CancellationException
import java.util.concurrent.CountDownLatch
import java.time.Duration

class CancellableFutureSpec extends UnitSpec {

  import ExecutionContext.Implicits.global

  "CancellableFuture" should "not run if cancelled" in withCancel {
    given ExecutionContext = neverExecutionContext
    val fut = CancellableFuture(fail())
    Cancellable.cancel()
    fut must beCanceled
  }

  it should "Cause future to finish early if future is running" in withCancel {
    val latch = new CountDownLatch(1)
    val fut = CancellableFuture {
      latch.countDown()
      // then wait forever
      Thread.sleep(Duration.ofSeconds(1))
      fail()
    }
    latch.await()
    Cancellable.cancel()

    fut must beCanceled
  }
}
