package io.github.edadma.riposte.router

import io.github.edadma.riposte.*

// A single URL query parameter, shaped exactly like `useState`. Where
// `useSearchParams` hands back the whole query as a `Params` map, `useQueryState`
// focuses on one typed key: the value lives in the URL (so it survives reload and
// is shareable/bookmarkable) but reads and writes like ordinary component state.
// It is sugar over the same `Location` store `useSearchParams` uses — the codec
// turns the URL's strings into `T` and back, and the setter merges its one key
// into the live query so independent keys never clobber each other.

// How a query value of type `T` crosses the string boundary of a URL. `parse`
// returns `None` on anything it can't read, which `useQueryState` treats as "use
// the default" — a garbage `?count=abc` reads back as the default rather than
// throwing. `render` is the inverse for the values the browser round-trips.
trait QueryCodec[T]:
  def parse(raw: String): Option[T]
  def render(value: T): String

// Codecs for the primitive types a query string carries day to day. They live in
// the companion object so they are found by `using` resolution with no import:
// `useQueryState("count", 0)` summons `QueryCodec[Int]` automatically, and a
// custom type supplies its own `given` (or passes one explicitly).
object QueryCodec:
  given string: QueryCodec[String] with
    def parse(raw: String): Option[String] = Some(raw)
    def render(value: String): String      = value

  given int: QueryCodec[Int] with
    def parse(raw: String): Option[Int] = raw.toIntOption
    def render(value: Int): String      = value.toString

  given long: QueryCodec[Long] with
    def parse(raw: String): Option[Long] = raw.toLongOption
    def render(value: Long): String      = value.toString

  given double: QueryCodec[Double] with
    def parse(raw: String): Option[Double] = raw.toDoubleOption
    def render(value: Double): String      = value.toString

  given boolean: QueryCodec[Boolean] with
    def parse(raw: String): Option[Boolean] = raw.toBooleanOption
    def render(value: Boolean): String      = value.toString

// Write `value` into `key` of the live query and navigate. Setting the default
// drops the key entirely, keeping URLs clean — an absent key reads back as the
// default anyway, so the two are indistinguishable except in the address bar.
// `push` opts into a new history entry; the default replaces the current one, so
// typing into a filter doesn't fill the Back button with intermediate states.
// The query is read fresh from `Location` at call time (not closed over at render
// time), and `Location.navigate` updates the URL synchronously — so two different
// keys set in the same tick each see the other's write and both survive.
private def writeQueryKey[T](key: String, default: T, codec: QueryCodec[T], value: T, push: Boolean): Unit =
  val cur  = parseQuery(Location.search())
  val next = if value == default then cur - key else cur.updated(key, codec.render(value))
  Location.setSearch(next, replace = !push)

// The setter half of the `useState`-shaped triple. A small class rather than a
// bare `T => Unit` only so it can carry the optional `push` flag through a default
// argument: `setCount(5)` replaces, `setCount(5, push = true)` pushes.
final class QSet[T] private[router] (key: String, default: T, codec: QueryCodec[T]):
  def apply(value: T, push: Boolean = false): Unit =
    writeQueryKey(key, default, codec, value, push)

// The functional-update half of the triple. `f` is applied to the *current* value
// read live from the URL, so `updateCount(_ + 1)` is correct even mid-burst.
final class QUpdate[T] private[router] (key: String, default: T, codec: QueryCodec[T]):
  def apply(f: T => T, push: Boolean = false): Unit =
    val cur = parseQuery(Location.search()).get(key).flatMap(codec.parse).getOrElse(default)
    writeQueryKey(key, default, codec, f(cur), push)

// One typed URL query parameter, returned as the same `(value, set, update)` triple
// as `useState`:
//
//   val (count, setCount, updateCount) = useQueryState("count", 0)
//   setCount(5)               // ?count=5, replacing the current history entry
//   setCount(5, push = true)  // ...as a new entry instead
//   updateCount(_ + 1)        // reads the live value, then writes
//
// The component re-renders whenever *this* key changes: the snapshot is the key's
// own typed value, so a change to an unrelated query key produces an equal snapshot
// and `useSyncExternalStore` bails out without re-rendering. An absent or
// unparseable value reads as `default`.
def useQueryState[T](key: String, default: T)(using codec: QueryCodec[T], h: Hooks): (T, QSet[T], QUpdate[T]) =
  val read: () => T = () => parseQuery(Location.search()).get(key).flatMap(codec.parse).getOrElse(default)
  val value         = useSyncExternalStore(Location.subscribe, read)
  (value, new QSet(key, default, codec), new QUpdate(key, default, codec))
