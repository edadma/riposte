---
title: "Data Fetching"
weight: 5
---

**riposte-query** is an async data layer in the shape of TanStack Query: a keyed cache of
server state with caching, deduplication, stale-while-revalidate refetching, and background
garbage collection. Where [atoms](/guide/state/) hold *client* state you own outright,
riposte-query holds *server* state you fetch, cache, and keep fresh. It's built on
riposte-atoms — each cached query is one atom — so it inherits the same fine-grained
subscriptions.

Add the dependency (it pulls in the core and atoms transitively):

```scala
libraryDependencies += "io.github.edadma" %%% "riposte-query" % "0.1.0"

import io.github.edadma.riposte.query.*
```

## Fetching a query

`useQuery` takes a **key** and a **fetcher** (a `() => Future[A]`) and returns the live
state of that query. The key names the cache entry; the fetcher is how a miss is filled:

```scala
val Todos = view {
  val q = useQuery(queryKey("todos"), () => api.fetchTodos())

  if q.isLoading then Spinner()
  else if q.isError then p(s"Failed: ${q.error.get.getMessage}")
  else ul(q.data.get.map(t => li(t.title)))
}
```

The result is a named tuple read by field:

- `data: Option[A]` — the last successful result, if any.
- `error: Option[Throwable]` — the last failure, if any.
- `isLoading: Boolean` — pending **and** no data yet (the first load).
- `isFetching: Boolean` — a fetch is in flight, including a background refetch over data
  already shown.
- `isError: Boolean` — the last settle was a failure.
- `refetch: () => Unit` — force a refetch, deduped against any fetch already running.

On first observe the query fetches; the component re-renders only when *this* query's cell
changes. Data already present stays on screen during a background refetch (`isFetching`
without `isLoading`) — stale-while-revalidate.

## Keys

A key is a structured `Vector[Any]`, built with `queryKey`. Include every input the fetch
depends on, so distinct inputs are distinct cache entries:

```scala
useQuery(queryKey("todo", id), () => api.fetchTodo(id))
```

Keys compare by value, so two calls with equal parts share one cache entry — and one fetch.
Prefix structure powers invalidation: `invalidatePrefix(queryKey("todo"))` reaches every
`["todo", *]` at once.

## Freshness and garbage collection

`QueryOptions` tunes per-query behaviour:

```scala
useQuery(
  queryKey("todos"),
  () => api.fetchTodos(),
  QueryOptions(staleTime = 30_000, gcTime = 5 * 60_000),
)
```

- `staleTime` (ms) — how long a successful result counts as **fresh**. Observing a fresh
  query does not refetch; past it, observing triggers a background refetch. Defaults to `0`
  (always stale, so every fresh mount revalidates).
- `gcTime` (ms) — how long an **unobserved** query is kept before the cache evicts it. When
  the last component watching a query unmounts, a countdown starts; if nothing observes it
  again in time, its cell is dropped (via the atoms `Store.forget` primitive). Defaults to
  five minutes.

## Cache control

For imperative control, reach the active client with `useQueryClient`:

```scala
val client = useQueryClient

client.invalidate(queryKey("todos"))          // mark stale; refetch if observed
client.invalidatePrefix(queryKey("todo"))     // every ["todo", *]
client.refetch(queryKey("todos"))             // force a refetch now
client.setQueryData(queryKey("todos"), next)  // write data directly (optimistic updates)
client.getQueryData[Seq[Todo]](queryKey("todos"))  // read without subscribing
```

`invalidate` marks a query stale and refetches it immediately if a component is watching;
an unobserved query is just flagged, so it refetches the next time it's observed.
`setQueryData` writes a fresh successful result into a query's cell — the seam for
optimistic updates — and `getQueryData` reads the current value without subscribing, handy
when computing that optimistic value from what's already cached.

## Scoping a client

A process-wide default client backs `useQuery` with no setup. To isolate a cache — for
tests, or a distinct data region — create a `QueryClient` and scope it with
`QueryClientProvider`:

```scala
val client = new QueryClient()

val App = view {
  QueryClientProvider(client) {
    Todos()
  }
}
```

Queries read under the provider use that client's cache instead of the default.
