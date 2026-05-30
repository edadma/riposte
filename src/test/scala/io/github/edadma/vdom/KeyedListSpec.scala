package io.github.edadma.vdom

// Keyed children: reuse-by-key across reorders, inserts, and removes, moving
// only the DOM blocks that are actually out of place.
class KeyedListSpec extends DomSuite:

  private def list(items: Seq[String]): VNode =
    ul(items.map(i => li(key := i, i)))

  test("preserves DOM identity across reordering"):
    val c = host()
    val root = createRoot(c)
    root.render(list(Seq("a", "b", "c")))
    val liA = c.querySelectorAll("li")(0)
    val liC = c.querySelectorAll("li")(2)
    assert(liA.textContent == "a")
    assert(liC.textContent == "c")

    root.render(list(Seq("c", "a", "b")))
    val reordered = c.querySelectorAll("li")
    assert(reordered(0).textContent == "c")
    assert(reordered(1).textContent == "a")
    assert(reordered(2).textContent == "b")
    // The moved nodes are the same DOM instances, not recreations.
    assert(reordered(0) eq liC)
    assert(reordered(1) eq liA)

  test("removes dropped items"):
    val c = host()
    val root = createRoot(c)
    root.render(list(Seq("a", "b", "c")))
    assert(c.querySelectorAll("li").length == 3)
    root.render(list(Seq("a", "c")))
    val rest = c.querySelectorAll("li")
    assert(rest.length == 2)
    assert(rest(0).textContent == "a")
    assert(rest(1).textContent == "c")

  test("inserts new items in the right position"):
    val c = host()
    val root = createRoot(c)
    root.render(list(Seq("a", "c")))
    val liA = c.querySelectorAll("li")(0)
    root.render(list(Seq("a", "b", "c")))
    val after = c.querySelectorAll("li")
    assert(after.length == 3)
    assert(after(0).textContent == "a")
    assert(after(1).textContent == "b")
    assert(after(2).textContent == "c")
    // The pre-existing 'a' was reused, not rebuilt.
    assert(after(0) eq liA)
