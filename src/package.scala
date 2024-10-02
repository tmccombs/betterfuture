package betterfuture

import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success, Try}

object syntax {
  import CFuture.syntax.*

  extension [T](f: Future[T]) {

    /** Ensure that `body` is run after the future completes,regardless of if
      * the future is successful.
      *
      * If `body` throws an exception, then the exception is reported to the
      * execution context.
      */
    def ensuring[U](body: => U)(using ExecutionContext): Future[T] =
      f.transform { t =>
        try {
          body
          t
        } catch {
          case e: Exception => mergeEx(t, e)
        }
      }

    /** Similar to [[ensuring]] but allows waiting for a `Future` instead.
      */
    def ensuringAsync[U](
        body: => Future[U]
    )(using ExecutionContext): Future[T] =
      f.transformWith { t =>
        body.transform { bodyResult =>
          bodyResult match {
            case Failure(e) => mergeEx(t, e)
            case _          => t
          }
        }(using ExecutionContext.parasitic)
      }

    /** If this fails, isntead complete with the result of another Future
      */
    def orElse[U >: T](other: => Future[U])(using ExecutionContext): Future[U] =
      f.transformWith(_.fold(_ => other, Future.successful))
  }
  private def mergeEx[T](t: Try[T], e: Throwable): Try[Nothing] = t.fold(
    { original =>
      original.addSuppressed(e)
      Failure(original)
    },
    _ => Failure(e)
  )
}
