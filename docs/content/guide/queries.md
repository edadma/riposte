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
libraryDependencies += "io.github.edadma" %%% "riposte-query" % "0.2.2"

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
- `isPlaceholderData: Boolean` — `data` is a placeholder or the previous key's value rather
  than this query's own settled result (see [placeholders](#placeholders-and-keeping-previous-data)).
- `refetch: () => Unit` — force a refetch, deduped against any fetch already running.
- `cancel: () => Unit` — abort the in-flight fetch (see [cancellation](#cancelling-a-fetch)).

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
- `retry` (count) — how many times a failed fetch is retried before its error surfaces.
  Defaults to `0`.
- `retryDelay` (`Int => Double`) — maps a zero-based attempt index to the delay (ms) before
  that retry. Defaults to exponential backoff (1s, 2s, 4s, …) capped at 30s.
- `refetchOnWindowFocus` / `refetchOnReconnect` (both default `true`) — refetch an observed,
  stale query when the window regains focus or the network comes back online.
- `enabled` (default `true`) — while `false` the query never fetches (not on observe, focus,
  reconnect, or interval) and stays pending; the knob for conditional and dependent queries.
- `refetchInterval` (`Option[Double]`, ms) — when set, polls the query at that interval while
  it is observed and enabled, refetching regardless of staleness.
- `keepPreviousData` (default `false`) — across a key change, keep the prior key's data
  visible (instead of a loading flash) until the new key's first fetch settles.

## Conditional and dependent queries

Set `enabled = false` to hold a query back until its inputs are ready — a query that depends
on the result of another, or one gated on user input. While disabled it stays pending and
never fetches; flipping it to `true` fetches if stale:

```scala
val user = useQuery(queryKey("user"), () => api.user())
val projects = useQuery(
  queryKey("projects", user.data.map(_.id)),
  () => api.projects(user.data.get.id),
  QueryOptions(enabled = user.data.isDefined),   // wait until the user is loaded
)
```

For background polling, set `refetchInterval` (ms) — the query refetches at that cadence
while it's observed and enabled, regardless of staleness.

## Placeholders and keeping previous data

To avoid a loading flash, give `useQuery` a `placeholderData` — a stand-in shown while the
query is pending with no data of its own. For paginated or filtered lists, `keepPreviousData`
holds the previous key's data on screen across a key change until the new fetch settles.
Either way, the result's `isPlaceholderData` is `true` while what you see isn't yet this
query's own settled value — handy for dimming stale content:

```scala
val q = useQuery(
  queryKey("photos", page),
  () => api.photos(page),
  QueryOptions(keepPreviousData = true),
  placeholderData = Some(Nil),
)
div(css("opacity" -> (if q.isPlaceholderData then "0.5" else "1")), renderPhotos(q.data.get))
```

To project the cached data into a derived shape, use `useSelectQuery(key, fetcher, select)` —
the cache still holds the raw value; only the result is transformed (e.g.
`useSelectQuery(queryKey("todos"), fetchTodos, _.length)` for just the count). It's a
distinct name rather than a `select` parameter because Scala can't both default `select` to
identity and infer the result type from it.

## Cache control

For imperative control, reach the active client with `useQueryClient`:

```scala
val client = useQueryClient

client.invalidate(queryKey("todos"))          // mark stale; refetch if observed
client.invalidatePrefix(queryKey("todo"))     // every ["todo", *]
client.refetch(queryKey("todos"))             // force a refetch now
client.setQueryData(queryKey("todos"), next)  // write data directly (optimistic updates)
client.setQueryData[Seq[Todo]](queryKey("todos"), _.getOrElse(Nil) :+ todo)  // update from current
client.getQueryData[Seq[Todo]](queryKey("todos"))  // read without subscribing
client.prefetchQuery(queryKey("todos"), () => api.fetchTodos())  // warm ahead of navigation
```

`invalidate` marks a query stale and refetches it immediately if a component is watching;
an unobserved query is just flagged, so it refetches the next time it's observed.
`setQueryData` writes a fresh successful result into a query's cell — the seam for
optimistic updates and for seeding a key before anything observes it (a later `useQuery`
adopts the seed and supplies the fetcher). Its updater form `setQueryData(key, prev => …)`
computes the next value from the current one (`prev` is `None` when nothing is cached yet).
`getQueryData` reads the current value without subscribing, handy when computing an
optimistic value. `prefetchQuery` eagerly loads a query into the cache without a component
observing it — for warming data ahead of navigation (an unadopted prefetch is evicted after
`gcTime`).

## Cancelling a fetch

The result's `cancel()` aborts the in-flight fetch (the client also cancels for you on its
own — e.g. a key change under `keepPreviousData`). For the abort to actually stop the
underlying request, the fetcher must wire it in: while a fetcher runs, the signal for that
call is available as `QueryFetch.signal`. Read it *synchronously* when you build the request
— not after an `await`/`flatMap`:

```scala
useQuery(queryKey("search", q), () =>
  dom.fetch(url, new dom.RequestInit { signal = QueryFetch.signal.orUndefined })
    .toFuture.flatMap(_.text().toFuture))
```

Exactly one fetcher runs at a time (JavaScript is single-threaded), so the ambient signal is
unambiguous; the request captures the signal object, which stays valid for its lifetime.

## Mutations

The write side of the cache. `useMutation` runs an asynchronous write and tracks its
lifecycle; unlike a query it isn't keyed or shared — each call owns its own state — so it
lives in component state rather than the cache. The client is still in reach, so a
mutation's callbacks can write the cache and invalidate queries:

```scala
val add = useMutation[Todo, Todo](
  mutationFn = todo => api.postTodo(todo),
  onSuccess  = (_, _) => client.invalidate(queryKey("todos")),
)

button(onClick := (_ => add.mutate(newTodo)), "Add")
if add.isPending then Spinner()
```

The result is a named tuple: `data` / `error` / `status` (`MutationStatus.{Idle,Pending,
Success,Error}`) and the `isIdle` / `isPending` / `isSuccess` / `isError` flags, plus
`mutate(vars)` (fire and ignore the outcome), `mutateAsync(vars): Future[D]` (await it), and
`reset()` (back to idle). Four callbacks fire around the write: `onMutate` just before it,
`onSuccess` / `onError` on the outcome, and `onSettled` after either.

**Optimistic updates with rollback** are the closure pattern — all four callbacks share the
component's refs and the captured client:

```scala
val snapshot = useRef[Option[List[Todo]]](None)
val add = useMutation[Todo, Todo](
  mutationFn = api.postTodo,
  onMutate   = todo =>
    snapshot.current = client.getQueryData[List[Todo]](key)
    client.setQueryData[List[Todo]](key, _.getOrElse(Nil) :+ todo),
  onError    = (_, _) => client.setQueryData(key, snapshot.current.getOrElse(Nil)),
  onSuccess  = (_, _) => client.invalidate(key),
)
```

## Infinite (paginated) queries

Where `useQuery` holds one result, `useInfiniteQuery` holds a growing list of pages. Give it
how to fetch one page from a page param, the first param, and how to derive the next param
from the pages so far:

```scala
val feed = useInfiniteQuery(
  queryKey("feed"),
  (cursor: Int) => api.feed(cursor),
  initialPageParam = 0,
  getNextPageParam = (last, _) => last.nextCursor,   // None ends the list
)

feed.pages.flatMap(_.items).map(renderItem)
if feed.hasNextPage then button(onClick := (_ => feed.fetchNextPage()), "Load more")
```

The result tuple adds `pages` (the convenience flattening of `data.pages`), `hasNextPage`,
and `fetchNextPage()` (loads and appends the next page; a no-op while one is in flight or
when none is left) to the familiar `data` / `error` / `isLoading` / `isFetching` / `isError`
/ `refetch`. The whole list is one observable snapshot, so the usual staleness, refetch, and
gc machinery applies to it unchanged.

For a list that grows from **both** ends — a chat window opened in the middle of its
history, scrolling up for newer and down for older — pass `getPreviousPageParam` too. The
query then exposes `hasPreviousPage` and `fetchPreviousPage()`, which **prepends** an older
page chosen from the first page held:

```scala
val msgs = useInfiniteQuery(
  queryKey("messages", roomId),
  (cursor: Long) => api.messages(roomId, cursor),
  initialPageParam = openAt,
  getNextPageParam     = (last, _)  => last.olderCursor,    // scroll down → history
  getPreviousPageParam = (first, _) => first.newerCursor,   // scroll up → newer
)
```

Omit `getPreviousPageParam` (its default returns `None`) for the common forward-only
"load more" case — `hasPreviousPage` then stays `false`.

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
