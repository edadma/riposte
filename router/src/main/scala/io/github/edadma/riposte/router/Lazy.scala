package io.github.edadma.riposte.router

import io.github.edadma.riposte.*
import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success}

// Defer loading a view until it is first rendered, showing a fallback until the
// chunk arrives — the building block for code-split routes. The view splits into
// its own bundle and is fetched on first visit rather than shipped up front.
//
// Pair it with Scala.js `js.dynamicImport`, whose `js.Promise` becomes a `Future`
// via `.toFuture` (an `ExecutionContext` must be in scope):
//
//   route("/reports")(
//     lazyView(() => js.dynamicImport(ReportsPage()).toFuture, fallback = Spinner())
//   )
//
// The fallback shows while the chunk is in flight; a load failure renders
// `onError(error)`. The load runs once per mount: navigating away and back
// reloads, so cache the imported component on the caller's side if that matters.

private val LazyComponent =
  component[(
      load:     () => Future[VNode],
      fallback: VNode,
      onError:  Throwable => VNode,
      ec:       ExecutionContext,
  )] { p =>
    given ExecutionContext = p.ec

    // None while loading, Some(Right) once loaded, Some(Left) if the load failed.
    val (state, setState, _) = useState[Option[Either[Throwable, VNode]]](None)

    useEffect(
      () =>
        p.load().onComplete {
          case Success(node) => setState(Some(Right(node)))
          case Failure(err)  => setState(Some(Left(err)))
        }
        noCleanup,
      Array(),
    )

    state match
      case None              => p.fallback
      case Some(Right(node)) => node
      case Some(Left(err))   => p.onError(err)
  }

// Render `load`'s view lazily: `fallback` until the future completes, then the
// loaded view, or `onError(error)` if it fails. See the file header for the
// `js.dynamicImport` bridge that makes this a code-split route.
def lazyView(
    load:     () => Future[VNode],
    fallback: VNode               = empty,
    onError:  Throwable => VNode  = _ => empty,
)(using ec: ExecutionContext): VNode =
  LazyComponent((load = load, fallback = fallback, onError = onError, ec = ec))
