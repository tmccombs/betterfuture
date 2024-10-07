package betterfuture

import java.util.concurrent.atomic.AtomicInteger
import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global

import UsingAsync.Manager.acquire
import scala.collection.mutable.Buffer
import scala.util.Using.Releasable

class UsingAsyncSpec extends UnitSpec {

  class TestResource(marker: AtomicInteger) extends AutoCloseable {
    override def close(): Unit = marker.set(0)

    def value: Int = marker.get()
    def incr(): Int = marker.getAndIncrement()
  }

  class ThrowingResource(message: String = "failed") extends AutoCloseable {
    override def close(): Unit = throw new Exception(message)
  }

  "apply" should "release resource" in {
    val marker = new AtomicInteger(4)
    val fut = UsingAsync(new TestResource(marker)) { r =>
      Future {
        r.incr()
        r.value
      }
    }
    whenReady(fut) { f =>
      f must be(5)
      marker.get() must be(0)
    }
  }

  "Manager" should "release all resources" in {
    val v1 = new AtomicInteger(1)
    val v2 = new AtomicInteger(2)
    val v3 = new AtomicInteger(4)

    val fut = UsingAsync.Manager {
      acquire(new TestResource(v1))
      Future {
        acquire(new TestResource(v2))
        acquire(new TestResource(v3))
      }
    }
    whenReady(fut) { _ =>
      v1.get() must be(0)
      v2.get() must be(0)
      v3.get() must be(0)
    }
  }

  it should "release on error" in {
    val c = new AtomicInteger(5)
    val fut = UsingAsync.Manager {
      acquire(new TestResource(c))
      Future.failed(new Exception)
    }

    whenReady(fut.failed) { _ =>
      c.get() must be(0)
    }
  }

  it should "merge errors from cleanup" in {
    val fut = UsingAsync.Manager {
      Future {
        acquire(new ThrowingResource("a"))
        acquire(new ThrowingResource("b"))
        throw new Exception("root")
      }
    }
    whenReady(fut.failed) { e =>
      e.getMessage() must be("root")
      val suppressed = e.getSuppressed()
      suppressed.length must be(2)
      // releasers are run in reverse order
      suppressed(0).getMessage() must be("b")
      suppressed(1).getMessage() must be("a")
    }
  }

  it should "release in opposite order from acquire" in {
    val released = Buffer.empty[Int]
    given Releasable[Int] with {
      def release(n: Int): Unit = released.addOne(n)
    }
    val fut = UsingAsync.Manager {
      acquire(1)
      Future {
        acquire(2)
        acquire(3)
      }
    }
    whenReady(fut) { _ =>
      released must contain inOrderOnly (3, 2, 1)
    }
  }
}
