package io.github.edadma.riposte.query

import org.scalajs.dom
import scala.scalajs.js

// The vocabulary of the query cache: how a query is named, the snapshot a cell
// holds, the options that govern its freshness, and the result a component reads.

// A query is named by a structured key — a vector of parts, like TanStack's
// `["todos", id]`. `Vector` already compares by value, so two keys with equal
// parts are the same cache entry for free, and prefix containment (`startsWith`)
// gives invalidate-by-prefix without any extra machinery: invalidating
// `Vector("todos")` reaches every `Vector("todos", *)`.
type QueryKey = Vector[Any]

// Build a key from its parts, so call sites read `queryKey("todos", id)` rather
// than `Vector[Any]("todos", id)`.
def queryKey(parts: Any*): QueryKey = parts.toVector

// Where a query is in its lifecycle, independent of whether a fetch is currently
// in flight (that is `isFetching`). `Pending` means no successful result yet.
enum QueryStatus:
  case Pending
  case Success
  case Error

// The immutable snapshot a query cell holds. `status` and `data`/`error` describe
// the last settled outcome; `isFetching` is true whenever a fetch is in flight,
// including a background refetch over already-present data; `updatedAt` is the
// epoch-ms time of the last successful settle (0 = never fetched).
final case class QueryState[A](
    status:     QueryStatus,
    data:       Option[A],
    error:      Option[Throwable],
    isFetching: Boolean,
    updatedAt:  Double,
):
  def isError: Boolean = status == QueryStatus.Error

object QueryState:
  // The snapshot a freshly-created cell starts from: pending, no data, never
  // fetched, not yet fetching (the fetch is kicked off on first observe).
  def initial[A]: QueryState[A] = QueryState(QueryStatus.Pending, None, None, isFetching = false, updatedAt = 0.0)

// Per-query knobs. `staleTime` is how long (ms) a successful result is considered
// fresh — within it, observing the query does not refetch; `gcTime` is how long
// (ms) an unobserved query's data is kept before the cache evicts it. `retry` is
// how many times a failed fetch is retried before its error surfaces, and
// `retryDelay` maps a zero-based attempt index to the delay (ms) before that
// retry — by default exponential backoff capped at 30s. `refetchOnWindowFocus`
// and `refetchOnReconnect` control whether an observed, stale query is refetched
// when the window regains focus or the network comes back.
//
// `enabled` gates the query: while false it never fetches (not on observe, focus,
// reconnect, or interval), staying pending — the knob for conditional and dependent
// queries ("wait until the id is known"); flipping it to true fetches if stale.
// `refetchInterval`, when set, polls the query every N ms while it is observed and
// enabled, refetching regardless of staleness. `keepPreviousData` keeps the prior
// key's data visible (instead of a loading flash) while a new key's first fetch is
// in flight — the hook reports that via `isPlaceholderData`.
final case class QueryOptions(
    staleTime:            Double         = 0.0,
    gcTime:               Double         = 5 * 60 * 1000.0,
    retry:                Int            = 0,
    retryDelay:           Int => Double  = QueryOptions.defaultRetryDelay,
    refetchOnWindowFocus: Boolean        = true,
    refetchOnReconnect:   Boolean        = true,
    enabled:              Boolean        = true,
    refetchInterval:      Option[Double] = None,
    keepPreviousData:     Boolean        = false,
)

object QueryOptions:
  // Exponential backoff: 1s, 2s, 4s, … capped at 30s, matching TanStack Query's
  // default. The argument is the just-failed attempt's zero-based index.
  val defaultRetryDelay: Int => Double = attempt => math.min(1000.0 * math.pow(2.0, attempt.toDouble), 30000.0)

// What a component gets back from `useQuery` — a named tuple so fields are read by
// name (`q.data`, `q.refetch`) in the spirit of `useState`'s destructured return,
// but with the richer surface an async resource needs. `isPlaceholderData` is true
// when `data` is showing a placeholder or the previous key's value rather than this
// query's own settled result (see `placeholderData` / `keepPreviousData`).
//
//   val q = useQuery(queryKey("todos"), fetchTodos)
//   if q.isLoading then spinner else renderTodos(q.data.get)
type QueryResult[A] = (
    data:              Option[A],
    error:             Option[Throwable],
    isLoading:         Boolean,
    isFetching:        Boolean,
    isError:           Boolean,
    isPlaceholderData: Boolean,
    refetch:           () => Unit,
)

// Timing and environment events indirected so tests can install deterministic
// fakes, the same seam pattern the core uses for `Transition`/`Timers`. In the
// browser `now` is wall-clock epoch ms, `schedule` is setTimeout/clearTimeout, and
// `subscribeFocus`/`subscribeOnline` register window 'focus'/'online' handlers,
// each returning an unsubscribe.
private[query] object QueryEnv:
  var now: () => Double = () => js.Date.now()

  var schedule: (() => Unit, Double) => (() => Unit) = (fn, delayMs) =>
    val id = dom.window.setTimeout(() => fn(), delayMs)
    () => dom.window.clearTimeout(id)

  var subscribeFocus: (() => Unit) => (() => Unit) = cb =>
    val handler: js.Function1[dom.Event, Unit] = _ => cb()
    dom.window.addEventListener("focus", handler)
    () => dom.window.removeEventListener("focus", handler)

  var subscribeOnline: (() => Unit) => (() => Unit) = cb =>
    val handler: js.Function1[dom.Event, Unit] = _ => cb()
    dom.window.addEventListener("online", handler)
    () => dom.window.removeEventListener("online", handler)
