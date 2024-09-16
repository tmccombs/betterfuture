package betterfuture

import scala.concurrent.{ExecutionContext, Future}
import scala.util.Success

class CFutureSpec extends UnitSpec {
  import ExecutionContext.Implicits.global
  import CFuture.syntax.*

  val v1 = TaskVal[Int]()
  val v2 = TaskVal[String]()

  "transforms" should "propagate context" in {
    val beSetCorrectly = be(Some(4))
    val fut = CFuture
      .withTaskVal(v1, 4)
      .unit
      .transform { t =>
        t.map(_ => v1.get must beSetCorrectly)
      }
      .andThen { case Success(_) =>
        v1.get must beSetCorrectly
      }
      .filter { _ =>
        v1.get.exists(_ == 4)
      }
      .map(_ => v1.get)

    fut.futureValue must be(Some(4))
  }

  it should "allow updates to TaskVals" in {
    v1.set(1024)
    CFuture {
      v1.get must be(Some(1024))
      v1.set(55)
      true
    }.map { _ =>
      v1.get must be(Some(55))
      v2.set("boo")
      true
    }.map { _ =>
      v2.get must be(Some("boo"))
    }.futureValue
  }

  "transformWith" should "propagate context" in {
    val beSetCorrectly = be(Some(13))
    val fut = CFuture
      .withTaskVal(v1, 13)
      .unit
      .transformWith { t =>
        withClue("transformWith")(v1.get must beSetCorrectly)
        Future.successful(Success(true))
      }
      .flatMap { _ =>
        withClue("flatMap")(v1.get must beSetCorrectly)
        Future.unit
      }
      .zipWith(Future.successful("hi")) { (_, s) =>
        withClue("zipWith")(v1.get must beSetCorrectly)
        s
      }
    fut.futureValue
  }

  it should "allow updates to TaskVals" in {
    v1.set(1024)
    CFuture
      .delegate {
        v1.get must be(Some(1024))
        v1.set(55)
        Future.unit
      }
      .flatMap { _ =>
        v1.get must be(Some(55))
        v2.set("boo")
        Future.unit
      }
      .flatMap { _ =>
        v2.get must be(Some("boo"))
        Future.unit
      }
      .futureValue
  }

  "zipWith" should "combine contexts" in {
    val fut = CFuture
      .withTaskVal(v1, 5)(true)
      .zipWith(CFuture.withTaskVal(v2, "apple").unit) { (v, _) =>
        withClue("zipWith") {
          v1.get must be(Some(5))
          v2.get must be(Some("apple"))
        }
        v
      }
      .zip(CFuture.withTaskVal(v1, 42).unit)
      .map { _ =>
        withClue("zip.map") {
          v1.get must be(Some(42))
          v2.get must be(Some("apple"))
        }
      }
    fut.futureValue
  }
}
