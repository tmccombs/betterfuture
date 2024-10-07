# betterfuture

A library to make scala Futures better.

## Motivation

The [`Future`](https://www.scala-lang.org/api/3.5.1/scala/concurrent/Future.html) type provided by the scala standard library is missing some feature I wish it had.
There are libraries like [Zio](https://zio.dev/), [Cats Effect](https://typelevel.org/cats-effect/), and even [Pekko](https://pekko.apache.org/) that provide
more complete frameworks, but it is still sometimes necessary to use scala's built-in `Future` type, perhaps for interoperability, or maybe because your codebase isn't
using one of those libraries yet.

This library aims to fill in the gaps and provide some missing functionality for scala `Future`s.

Features included in this library are:

- Ability to cancel `Future`s. See [CancellableFuture](src/CancellableFuture.scala),
  [InterruptibleFuture](src/InterruptibleFuture.scala), and [Cancellable](src/Cancellable.scala).
- Having Future-local storage that is propagated through `Future`s, similar to
  thread-local storage. See [CFuture](src/CFuture.scala) and [TaskVal](src/TaskVal.scala).
- Asynchronous equivalent of `Using`
- [ ] Asynchronous channel for passing values between future tasks. (not yet implemented)
- [ ] Asynchronous iterator trait (not yet implemented)
- [ ] Asynchronous lock (not yet implemented)
- [ ] Asynchronous semaphore, or rate limiter (not yet implemented)
