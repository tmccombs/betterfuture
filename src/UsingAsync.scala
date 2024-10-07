package betterfuture

import scala.util.Using.Releasable
import scala.util.Failure
import scala.concurrent.{Future, ExecutionContext}
import betterfuture.syntax.*

object UsingAsync {

  final class Manager {
    import Manager.Resource

    private var closed = false
    private var resources: List[Resource[?]] = Nil

    def acquire[R: Releasable](resource: R): Unit = {
      if (resource == null) throw new NullPointerException("null resource")
      if (closed)
        throw new IllegalStateException("Manager has already been closed")
      resources = new Resource(resource) :: resources
    }

    def apply[R: Releasable](resource: R): resource.type = {
      acquire(resource)
      resource
    }

    private def manage[A](
        op: Manager ?=> Future[A]
    )(using ExecutionContext): Future[A] =
      Future.delegate(op(using this)).transform { t =>
        closed = true
        var toThrow: Throwable = t.fold(e => e, _ => null)
        var rs = resources
        resources = null // allow GC
        while (rs.nonEmpty) {
          val resource = rs.head
          rs = rs.tail
          try resource.release()
          catch {
            case t: Throwable =>
              if (toThrow == null) toThrow = t
              else {
                toThrow.addSuppressed(t)
              }
          }
        }

        if (toThrow != null) {
          Failure(toThrow)
        } else {
          t
        }
      }
  }

  object Manager {
    private final class Resource[R](resource: R)(using
        releasable: Releasable[R]
    ) {
      def release(): Unit = releasable.release(resource)
    }

    def apply[T](body: Manager ?=> Future[T])(using
        ExecutionContext
    ): Future[T] =
      (new Manager).manage(body)

    def acquire[R: Releasable](resource: R)(using m: Manager) =
      m.acquire(resource)
  }
  def apply[R: Releasable, A](
      res: => R
  )(f: R => Future[A])(using ExecutionContext): Future[A] = resource(res)(f)

  def resource[R: Releasable, A](resource: R)(body: R => Future[A])(using
      ExecutionContext
  ): Future[A] =
    Future
      .delegate(body(resource))
      .ensuring(summon[Releasable[R]].release(resource))

  // TODO: macro for resources?

}
