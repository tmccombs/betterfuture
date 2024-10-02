package betterfuture

import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global
import org.scalatest.LoneElement.*
import betterfuture.syntax.*

import java.util.concurrent.atomic.AtomicInteger

class EnsuringSpec extends UnitSpec {
  "ensuring" should "run when future succeeds" in {
    val counter = new AtomicInteger
    val fut = Future.unit.ensuring(counter.getAndIncrement())

    whenReady(fut)(_ => counter.get() must be(1))
  }

  it should "run when future throws an error" in {
    val counter = new AtomicInteger
    val fut = Future {
      throw new Exception("test")
    }.ensuring(counter.getAndIncrement())

    whenReady(fut.failed) { e =>
      e.getMessage() must be("test")
      counter.get() must be(1)
    }
  }

  it should "merge errors" in {
    val fut = Future {
      throw new Exception("first")
    }.ensuring {
      throw new Exception("second")
    }

    whenReady(fut.failed) { e =>
      e.getMessage() must be("first")
      e.getSuppressed().loneElement.getMessage() must be("second")
    }
  }

  "ensuringAsync" should "run when future succeeds" in {
    val counter = new AtomicInteger
    val fut = Future.unit.ensuringAsync(Future(counter.getAndIncrement()))

    whenReady(fut)(_ => counter.get() must be(1))
  }

  it should "run when future throws an error" in {
    val counter = new AtomicInteger
    val fut = Future {
      throw new Exception("test")
    }.ensuringAsync(Future(counter.getAndIncrement()))

    whenReady(fut.failed) { e =>
      e.getMessage() must be("test")
      counter.get() must be(1)
    }
  }

  it should "merge errors" in {
    val fut = Future {
      throw new Exception("first")
    }.ensuringAsync {
      Future.failed(new Exception("second"))
    }

    whenReady(fut.failed) { e =>
      e.getMessage() must be("first")
      e.getSuppressed().loneElement.getMessage() must be("second")
    }
  }
}
