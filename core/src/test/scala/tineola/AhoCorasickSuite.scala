package tineola

import java.nio.charset.StandardCharsets.UTF_8

class AhoCorasickSuite extends munit.FunSuite {

  private def search(patterns: Seq[String], haystack: String): Set[(Int, Int, Int)] =
    AhoCorasick(patterns).findAll(haystack).map(m => (m.pattern, m.start, m.end)).toSet

  private def searchNoTeddy(patterns: Seq[String], haystack: String): Set[(Int, Int, Int)] =
    AhoCorasick.builder
      .addAll(patterns.map(_.getBytes(UTF_8)))
      .enableTeddy(false)
      .build()
      .findAll(haystack)
      .map(m => (m.pattern, m.start, m.end))
      .toSet

  test("single pattern") {
    assertEquals(search(Seq("abc"), "xabcx"), Set((0, 1, 4)))
  }

  test("multiple non-overlapping") {
    assertEquals(
      search(Seq("foo", "bar"), "foobar"),
      Set((0, 0, 3), (1, 3, 6))
    )
  }

  test("overlapping patterns") {
    assertEquals(
      search(Seq("he", "she", "his", "hers"), "ushers"),
      Set((1, 1, 4), (0, 2, 4), (3, 2, 6))
    )
  }

  test("pattern is prefix of another") {
    assertEquals(
      search(Seq("ab", "abc"), "xabcx"),
      Set((0, 1, 3), (1, 1, 4))
    )
  }

  test("repeated occurrences") {
    assertEquals(
      search(Seq("aa"), "aaaa"),
      Set((0, 0, 2), (0, 1, 3), (0, 2, 4))
    )
  }

  test("no matches") {
    assertEquals(search(Seq("xyz"), "abc"), Set.empty[(Int, Int, Int)])
  }

  test("match at start and end") {
    assertEquals(
      search(Seq("ab", "cd"), "abxxcd"),
      Set((0, 0, 2), (1, 4, 6))
    )
  }

  test("long haystack triggers teddy") {
    val hay = "x" * 50 + "needle" + "y" * 50
    assertEquals(
      search(Seq("needle"), hay),
      Set((0, 50, 56))
    )
  }

  test("teddy and DAT agree on same inputs") {
    val patterns = Seq("alpha", "beta", "gamma", "delta")
    val hay = "alpha" * 10 + "beta" + "gamma" * 5 + "delta" + "noise" * 20
    assertEquals(search(patterns, hay), searchNoTeddy(patterns, hay))
  }

  test("byte patterns with high bits") {
    val patterns = Array(Array(0xc3.toByte, 0xa9.toByte))
    val hay = Array(0x20.toByte, 0xc3.toByte, 0xa9.toByte, 0x20.toByte)
    val ac = AhoCorasick.fromBytes(patterns.toIndexedSeq)
    assertEquals(ac.findAll(hay).toList, List(Match(0, 1, 3)))
  }

  test("findFirst") {
    val ac = AhoCorasick(Seq("foo", "bar"))
    assertEquals(ac.findFirst("xxbarxxfoo").map(_.pattern), Some(1))
  }

  test("findFirst short-circuits on large haystack") {
    val ac = AhoCorasick(Seq("needle"))
    val hay = "needle" + "x" * 1000000
    assertEquals(ac.findFirst(hay), Some(Match(0, 0, 6)))
  }

  test("findFirst no match") {
    val ac = AhoCorasick(Seq("xyz"))
    assertEquals(ac.findFirst("abcabc" * 100), None)
  }
}
