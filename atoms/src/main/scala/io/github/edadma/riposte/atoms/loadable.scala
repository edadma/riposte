package io.github.edadma.riposte.atoms

import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success}

// The state of an asynchronous value. There is no Suspense here, so a component
// reads the `Loadable` directly and renders each case — a loading indicator, the
// data, or an error — rather than the runtime suspending the render.
enum Loadable[+A]:
  case Loading
  case Data(value: A)
  case Errored(error: Throwable)

// An atom whose value is produced by a `Future`. It reads as `Loading` until the
// future settles, then as `Data` or `Errored`. The future is started lazily, the
// first time the atom is observed (via `onMount`), so an unused loadable never
// runs its work; the result is pushed back through `setSelf`.
//
// v1 has no dependency tracking on the async source — `run` is a plain by-name
// future, not a `Get => Future[A]` — so a loadable does not re-run when some other
// atom changes. (Re-fetch by recreating the atom, e.g. via an `atomFamily` keyed
// on the query.)
def atomLoadable[A](run: => Future[A])(using ec: ExecutionContext): Atom[Loadable[A]] =
  val state = atom[Loadable[A]](Loadable.Loading)
  onMount(state) { setSelf =>
    run.onComplete {
      case Success(v) => setSelf(Loadable.Data(v))
      case Failure(e) => setSelf(Loadable.Errored(e))
    }
    None
  }
  state
