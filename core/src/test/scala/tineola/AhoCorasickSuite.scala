package tineola

import java.nio.charset.StandardCharsets.UTF_8

class AhoCorasickSuite extends munit.FunSuite {

  private def overlapping(patterns: Seq[String], haystack: String): List[(Int, Int, Int)] =
    AhoCorasick(patterns).findOverlapping(haystack).map(m => (m.pattern, m.start, m.end)).toList

  test("single pattern") {
    assertEquals(AhoCorasick("abc").findAll("xabcx").toList, List(Match(0, 1, 4)))
  }

  test("multiple non-overlapping") {
    assertEquals(
      AhoCorasick("foo", "bar").findAll("foobar").toList,
      List(Match(0, 0, 3), Match(1, 3, 6))
    )
  }

  test("overlapping patterns") {
    assertEquals(
      overlapping(Seq("he", "she", "his", "hers"), "ushers"),
      List((1, 1, 4), (0, 2, 4), (3, 2, 6))
    )
  }

  test("pattern is prefix of another") {
    assertEquals(overlapping(Seq("ab", "abc"), "xabcx"), List((0, 1, 3), (1, 1, 4)))
  }

  test("repeated occurrences") {
    assertEquals(overlapping(Seq("aa"), "aaaa"), List((0, 0, 2), (0, 1, 3), (0, 2, 4)))
    assertEquals(AhoCorasick("aa").findAll("aaaa").toList, List(Match(0, 0, 2), Match(0, 2, 4)))
  }

  test("no matches") {
    val ac = AhoCorasick("xyz")
    assertEquals(ac.findAll("abc").toList, Nil)
    assert(!ac.isMatch("abc"))
  }

  test("match at start and end") {
    assertEquals(
      AhoCorasick("ab", "cd").findAll("abxxcd").toList,
      List(Match(0, 0, 2), Match(1, 4, 6))
    )
  }

  test("long haystack triggers teddy") {
    val hay = "x" * 50 + "needle" + "y" * 50
    assertEquals(AhoCorasick("needle").findAll(hay).toList, List(Match(0, 50, 56)))
  }

  test("teddy and scalar agree on same inputs") {
    val patterns = Seq("alpha", "beta", "gamma", "delta")
    val hay = "alpha" * 10 + "beta" + "gamma" * 5 + "delta" + "noise" * 20
    val scalar = AhoCorasick(patterns, AhoCorasick.Options(simd = false))
    assertEquals(
      AhoCorasick(patterns).findOverlapping(hay).toList,
      scalar.findOverlapping(hay).toList
    )
  }

  test("byte patterns with high bits") {
    val patterns = Array(Array(0xc3.toByte, 0xa9.toByte))
    val hay = Array(0x20.toByte, 0xc3.toByte, 0xa9.toByte, 0x20.toByte)
    val ac = AhoCorasick.fromBytes(patterns.toIndexedSeq)
    assertEquals(ac.findAll(hay).toList, List(Match(0, 1, 3)))
  }

  test("sibling bytes on both sides of 0x80") {
    val patterns = Seq(
      Array(0x00, 0x00, 0x80),
      Array(0x00, 0x00, 0xff),
      Array(0x00, 0x00, 0x00),
      Array(0x00, 0x7f),
      Array(0x7f, 0x00),
      Array(0x7f, 0x7f)
    ).map(_.map(_.toByte))
    val hay = Array(0x00, 0x00, 0x80, 0x7f, 0x7f, 0x00, 0x00, 0xff).map(_.toByte)
    val ac = AhoCorasick.fromBytes(patterns)
    assertEquals(
      ac.findOverlapping(hay).toList,
      List(Match(0, 0, 3), Match(5, 3, 5), Match(4, 4, 6), Match(1, 5, 8))
    )
  }

  test("findFirst") {
    val ac = AhoCorasick("foo", "bar")
    assertEquals(ac.findFirst("xxbarxxfoo").map(_.pattern), Some(1))
  }

  test("findFirst short-circuits on large haystack") {
    val ac = AhoCorasick("needle")
    val hay = "needle" + "x" * 1000000
    assertEquals(ac.findFirst(hay), Some(Match(0, 0, 6)))
  }

  test("findFirst no match") {
    val ac = AhoCorasick("xyz")
    assertEquals(ac.findFirst("abcabc" * 100), None)
  }

  test("match order does not depend on haystack length") {
    val ac = AhoCorasick("abcd", "bc")
    val hay = "x" * 60 + "abcd" + "xxx" + "abcd"
    assertEquals(ac.findFirst("abcd"), Some(Match(0, 0, 4)))
    assertEquals(ac.findFirst("abcd" + "x" * 60), Some(Match(0, 0, 4)))
    assertEquals(ac.findAll(hay).toList, List(Match(0, 60, 64), Match(0, 67, 71)))
    assertEquals(
      ac.findOverlapping(hay).toList,
      List(Match(0, 60, 64), Match(1, 61, 63), Match(0, 67, 71), Match(1, 68, 70))
    )
  }

  test("leftmost-first prefers the pattern listed first") {
    val ac = AhoCorasick("append", "appendage", "app")
    assertEquals(
      ac.findAll("append the app to the appendage").map(_.pattern).toList,
      List(0, 2, 0)
    )
  }

  test("leftmost-longest prefers the longest match") {
    val options = AhoCorasick.Options(matchKind = MatchKind.LeftmostLongest)
    val ac = AhoCorasick(Seq("append", "appendage", "app"), options)
    assertEquals(
      ac.findAll("append the app to the appendage").map(_.pattern).toList,
      List(0, 2, 1)
    )
  }

  test("string offsets are char indices") {
    val s = "café bar 😀 bar"
    val ms = AhoCorasick("bar").findAll(s).toList
    assertEquals(ms.map(_.start), List(5, 12))
    assertEquals(ms.map(m => s.substring(m.start, m.end)), List("bar", "bar"))
  }

  test("isMatch") {
    val ac = AhoCorasick("foo", "bar")
    assert(ac.isMatch("xxbarxx"))
    assert(!ac.isMatch("xxbazxx"))
  }

  test("replaceAll") {
    val ac = AhoCorasick("foo", "bar")
    assertEquals(
      ac.replaceAll("foo and bar, not baz")(m => "*" * m.length),
      "*** and ***, not baz"
    )
    val bytes = ac.replaceAll("foobar".getBytes(UTF_8))(_ => "-".getBytes(UTF_8))
    assertEquals(new String(bytes, UTF_8), "--")
  }

  test("sub-range search reports absolute offsets") {
    val ac = AhoCorasick("ab")
    val h = "abxxabxxab".getBytes(UTF_8)
    assertEquals(ac.findAll(h, 1, 9).toList, List(Match(0, 4, 6)))
    intercept[IndexOutOfBoundsException](ac.findAll(h, 5, 2))
  }

  test("patterns are copied at construction") {
    val p = "needle".getBytes(UTF_8)
    val ac = AhoCorasick.fromBytes(Seq(p))
    p(0) = 'N'.toByte
    assertEquals(ac.findAll("x" * 100 + "needle" + "x" * 100).toList, List(Match(0, 100, 106)))
  }

  test("findOverlapping across block boundaries") {
    val hay = "x" * 65534 + "abcd" + "x" * 10
    assertEquals(
      AhoCorasick("abcd", "cd").findOverlapping(hay).toList,
      List(Match(0, 65534, 65538), Match(1, 65536, 65538))
    )
  }
}
