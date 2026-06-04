package io.github.edadma.riposte.query

import io.github.edadma.riposte.*
import scala.concurrent.Future

// The component-facing surface: a context carrying the active `QueryClient`, a
// provider to scope one over a subtree, and `useQuery` to read a query.

// The ambient client. There is a process-wide default so `useQuery` works with no
// setup; wrap a subtree in `QueryClientProvider` to scope an isolated cache (tests,
// or distinct data regions). Built lazily off the global execution context so the
// default exists without the caller wiring one.
val QueryClientContext: Context[QueryClient] =
  createContext(new QueryClient()(using scala.concurrent.ExecutionContext.global))

// Scope a `QueryClient` to a subtree. Queries read under `child` use this client's
// cache instead of the default.
def QueryClientProvider(client: QueryClient)(child: VNode): VNode =
  QueryClientContext.provide(client, child)

// The active client, for imperative cache control from inside a component
// (`invalidate`, `setQueryData`, …).
def useQueryClient(using Hooks): QueryClient = useContext(QueryClientContext)

// Subscribe a component to a query. On first observe the query fetches (if stale
// and enabled); the result drives `data`/`error`/`status`, and the component
// re-renders only when this query's cell changes. `staleTime`/`gcTime` tune
// freshness and eviction, `enabled` gates fetching, `refetchInterval` polls. Returns
// a `QueryResult` named tuple.
//
//   val q = useQuery(queryKey("todos", userId), () => api.todos(userId))
//   if q.isLoading then Spinner() else TodoList(q.data.get)
//
// `placeholderData` supplies a stand-in shown while the query is pending with no
// data of its own (reported via `isPlaceholderData`); `keepPreviousData` (in
// `options`) shows the prior key's value across a key change instead of a loading
// flash. To project the cached data into a derived shape, use `useSelectQuery`.
def useQuery[A](
    key:             QueryKey,
    fetcher:         () => Future[A],
    options:         QueryOptions = QueryOptions(),
    placeholderData: Option[A]    = None,
)(using Hooks): QueryResult[A] =
  runQuery(key, fetcher, options, identity, placeholderData)

// Like `useQuery`, but projects the cached data through `select` before it reaches
// the component — `useSelectQuery(key, fetcher, _.name)` yields a `QueryResult` of
// the names. The cache still holds the raw `A`; only the result is transformed. (A
// distinct name rather than a `select` parameter on `useQuery`, because Scala can't
// both default `select` to identity and infer the result type from it.)
def useSelectQuery[A, B](
    key:             QueryKey,
    fetcher:         () => Future[A],
    select:          A => B,
    options:         QueryOptions = QueryOptions(),
    placeholderData: Option[B]    = None,
)(using Hooks): QueryResult[B] =
  runQuery(key, fetcher, options, select, placeholderData)

// The shared body of `useQuery`/`useSelectQuery`. The cell holds `QueryState[Any]`;
// its data is cast back to `A` (sound because this key's fetcher produced it) and
// run through `select` to `B`. `placeholderData` and `keepPreviousData` together
// decide what `data` shows while the query has no settled value of its own.
private def runQuery[A, B](
    key:             QueryKey,
    fetcher:         () => Future[A],
    options:         QueryOptions,
    select:          A => B,
    placeholderData: Option[B],
)(using Hooks): QueryResult[B] =
  val client = useQueryClient
  val cell   = client.register(key, fetcher.asInstanceOf[() => Future[Any]], options)

  // Subscribe through the client's store; the callback is stable per cell so a
  // steady query never re-subscribes.
  val subscribe = useCallback((cb: () => Unit) => client.store.sub(cell, cb), Array(cell))
  val st        = useSyncExternalStore(subscribe, () => client.store.get(cell))

  val selected: Option[B] = st.data.map(a => select(a.asInstanceOf[A]))

  // Remember the last real selected value so `keepPreviousData` can hold the prior
  // key's data across a key change while the new key's first fetch is in flight. The
  // ref survives the re-render that swaps the key; the new cell starts pending.
  val previous = useRef[Option[B]](None)
  if selected.isDefined then previous.current = selected

  val shown: Option[B] =
    if selected.isDefined then selected
    else if options.keepPreviousData && previous.current.isDefined then previous.current
    else placeholderData

  (
    data              = shown,
    error             = st.error,
    isLoading         = options.enabled && st.status == QueryStatus.Pending && shown.isEmpty,
    isFetching        = st.isFetching,
    isError           = st.status == QueryStatus.Error,
    isPlaceholderData = selected.isEmpty && shown.isDefined,
    refetch           = () => client.refetch(key),
  )
