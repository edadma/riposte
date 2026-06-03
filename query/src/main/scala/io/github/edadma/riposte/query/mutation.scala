package io.github.edadma.riposte.query

import io.github.edadma.riposte.*
import scala.concurrent.Future
import scala.util.{Failure, Success}

// Mutations: the write side of the cache. Unlike a query, a mutation is not keyed
// or shared — each `useMutation` call owns its own state — so it lives in component
// state (core `useState`) rather than in the `QueryClient`'s store. The client is
// still in reach (via `useQueryClient`) so a mutation's lifecycle callbacks can
// optimistically write the cache and invalidate queries on success.

// Where a mutation is in its lifecycle. `Idle` before it has run, `Pending` while
// the mutation function is in flight, then `Success` or `Error`.
enum MutationStatus:
  case Idle, Pending, Success, Error

// The immutable snapshot of a mutation's state.
final case class MutationState[D](
    status: MutationStatus,
    data:   Option[D],
    error:  Option[Throwable],
)
object MutationState:
  def idle[D]: MutationState[D] = MutationState(MutationStatus.Idle, None, None)

// What `useMutation` returns — a named tuple, like `useQuery`'s result. `mutate`
// fires the mutation and ignores the outcome (the result drives the UI); the
// `…Async` form hands back the `Future` for callers that need to await it.
// `reset` returns a settled mutation to `Idle`.
type MutationResult[V, D] = (
    data:        Option[D],
    error:       Option[Throwable],
    status:      MutationStatus,
    isIdle:      Boolean,
    isPending:   Boolean,
    isSuccess:   Boolean,
    isError:     Boolean,
    mutate:      V => Unit,
    mutateAsync: V => Future[D],
    reset:       () => Unit,
)

// Run an asynchronous write and track its lifecycle. `mutationFn` takes the
// variables of one invocation and returns the result future. The callbacks fire
// around it: `onMutate` just before the fetch (where an optimistic cache write
// goes), `onSuccess`/`onError` on the outcome, and `onSettled` after either.
//
// Optimistic update with rollback is the closure pattern: snapshot the cache into
// a `useRef` in `onMutate`, write the optimistic value, and restore the snapshot
// in `onError` — all three callbacks are defined in the same render, so they share
// the component's refs and the captured `QueryClient`.
//
//   val snapshot = useRef[Option[List[Todo]]](None)
//   val add = useMutation[Todo, Todo](
//     mutationFn = postTodo,
//     onMutate   = todo =>
//       snapshot.current = client.getQueryData[List[Todo]](key)
//       client.setQueryData[List[Todo]](key, _.getOrElse(Nil) :+ todo),
//     onError    = (_, _) => client.setQueryData(key, snapshot.current.getOrElse(Nil)),
//     onSuccess  = (_, _) => client.invalidate(key),
//   )
def useMutation[V, D](
    mutationFn: V => Future[D],
    onMutate:   V => Unit                                 = (_: V) => (),
    onSuccess:  (D, V) => Unit                            = (_: D, _: V) => (),
    onError:    (Throwable, V) => Unit                    = (_: Throwable, _: V) => (),
    onSettled:  (Option[D], Option[Throwable], V) => Unit = (_: Option[D], _: Option[Throwable], _: V) => (),
)(using Hooks): MutationResult[V, D] =
  val client = useQueryClient
  given scala.concurrent.ExecutionContext = client.executionContext

  val (state, setState, _) = useState(MutationState.idle[D])

  // The future can settle after the component unmounts; this guard keeps a late
  // settle from writing the gone component's state. The result is still delivered
  // to the callbacks (which may touch the cache) — only the local state write is
  // skipped.
  val mounted = useRef(true)
  useEffect(() => () => mounted.current = false, Array())

  def run(vars: V): Future[D] =
    setState(MutationState(MutationStatus.Pending, None, None))
    onMutate(vars)
    val f = mutationFn(vars)
    f.onComplete {
      case Success(d) =>
        if mounted.current then setState(MutationState(MutationStatus.Success, Some(d), None))
        onSuccess(d, vars)
        onSettled(Some(d), None, vars)
      case Failure(err) =>
        if mounted.current then setState(MutationState(MutationStatus.Error, None, Some(err)))
        onError(err, vars)
        onSettled(None, Some(err), vars)
    }
    f

  (
    data        = state.data,
    error       = state.error,
    status      = state.status,
    isIdle      = state.status == MutationStatus.Idle,
    isPending   = state.status == MutationStatus.Pending,
    isSuccess   = state.status == MutationStatus.Success,
    isError     = state.status == MutationStatus.Error,
    mutate      = (v: V) => { run(v); () },
    mutateAsync = (v: V) => run(v),
    reset       = () => setState(MutationState.idle[D]),
  )
