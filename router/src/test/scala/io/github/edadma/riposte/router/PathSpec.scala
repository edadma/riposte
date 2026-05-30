package io.github.edadma.riposte.router

import org.scalatest.funsuite.AnyFunSuite

// The matcher is pure, so it needs no DOM: patterns against paths, the captured
// params, and the specificity ordering the router relies on to pick a winner.
class PathSpec extends AnyFunSuite:

  // The same ordering `bestMatch` uses; summoning it here also proves it exists.
  private val ord = summon[Ordering[(Int, Int, Int)]]

  test("a literal pattern matches only that path"):
    assert(matchPath("/about", "/about") == Some(Map.empty))
    assert(matchPath("/about", "/contact") == None)

  test("a :name segment captures that path segment"):
    assert(matchPath("/users/:id", "/users/7") == Some(Map("id" -> "7")))
    assert(matchPath("/users/:id/posts/:pid", "/users/7/posts/3") ==
      Some(Map("id" -> "7", "pid" -> "3")))

  test("a trailing * captures the remainder"):
    assert(matchPath("/files/*", "/files/a/b/c") == Some(Map("*" -> "a/b/c")))
    assert(matchPath("*", "/anything/here") == Some(Map("*" -> "anything/here")))

  test("root matches only root"):
    assert(matchPath("/", "/") == Some(Map.empty))
    assert(matchPath("/", "/about") == None)

  test("lengths must match when there is no splat"):
    assert(matchPath("/users/:id", "/users") == None)
    assert(matchPath("/users", "/users/7") == None)

  test("specificity ranks literal over param over splat, and a path over a catch-all"):
    assert(ord.gt(specificity("/users/new"), specificity("/users/:id")))
    assert(ord.gt(specificity("/users/:id"), specificity("/users/*")))
    assert(ord.gt(specificity("/"), specificity("*")))

  test("matchPrefix consumes a prefix and returns the remainder"):
    assert(matchPrefix(segments("/users"), segments("/users/7/posts")) ==
      Some((Map.empty, Vector("7", "posts"))))
    assert(matchPrefix(segments("/users/:id"), segments("/users/7/posts")) ==
      Some((Map("id" -> "7"), Vector("posts"))))

  test("an empty (index) pattern consumes nothing"):
    assert(matchPrefix(segments(""), segments("/x/y")) == Some((Map.empty, Vector("x", "y"))))
    assert(matchPrefix(segments(""), segments("/")) == Some((Map.empty, Vector.empty)))

  test("matchPrefix fails when a literal segment differs"):
    assert(matchPrefix(segments("/users"), segments("/posts/1")) == None)

  test("query strings round-trip through parse and encode"):
    assert(parseQuery("q=hello&page=2") == Map("q" -> "hello", "page" -> "2"))
    assert(parseQuery("") == Map.empty)
    assert(parseQuery("flag") == Map("flag" -> ""))
    assert(parseQuery("q=a+b%26c") == Map("q" -> "a b&c")) // '+' is space, %26 is '&'
    assert(parseQuery(encodeQuery(Map("q" -> "a b&c", "n" -> "1"))) ==
      Map("q" -> "a b&c", "n" -> "1"))
