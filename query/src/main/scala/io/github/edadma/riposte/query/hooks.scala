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

// Subscribe a component to a query. On first observe the query fetches (if stale);
// the result drives `data`/`error`/`status`, and the component re-renders only
// when this query's cell changes. `staleTime` and `gcTime` tune freshness and
// eviction. Returns a `QueryResult` named tuple.
//
//   val q = useQuery(queryKey("todos", userId), () => api.todos(userId))
//   if q.isLoading then Spinner() else TodoList(q.data.get)
def useQuery[A](
    key:     QueryKey,
    fetcher: () => Future[A],
    options: QueryOptions = QueryOptions(),
)(using Hooks): QueryResult[A] =
  val client = useQueryClient
  val cell   = client.register(key, fetcher, options)

  // Subscribe through the client's store; the callback is stable per cell so a
  // steady query never re-subscribes. The cell holds `QueryState[Any]`; the data
  // is cast back to `A` on read, sound because the fetcher for this key produced it.
  val subscribe = useCallback((cb: () => Unit) => client.store.sub(cell, cb), Array(cell))
  val st        = useSyncExternalStore(subscribe, () => client.store.get(cell))

  (
    data       = st.data.map(_.asInstanceOf[A]),
    error      = st.error,
    isLoading  = st.status == QueryStatus.Pending && st.data.isEmpty,
    isFetching = st.isFetching,
    isError    = st.status == QueryStatus.Error,
    refetch    = () => client.refetch(key),
  )
