package tineola

import java.nio.charset.StandardCharsets.UTF_8
import jdk.incubator.vector.ByteVector

class TeddySuite extends munit.FunSuite {

  private def both(
      patterns: Seq[String],
      hay: String
  ): (Set[(Int, Int, Int)], Set[(Int, Int, Int)]) = {
    val ps = patterns.map(_.getBytes(UTF_8))
    val teddy = AhoCorasick.builder.addAll(ps).enableTeddy(true).build()
    val dat = AhoCorasick.builder.addAll(ps).enableTeddy(false).build()
    val h = hay.getBytes(UTF_8)
    def collect(ac: AhoCorasick) =
      ac.findAll(h).map(m => (m.pattern, m.start, m.end)).toSet
    (collect(teddy), collect(dat))
  }

  test("slim1: single-byte patterns") {
    val (t, d) = both(Seq("a", "b", "c"), "abracadabra" * 10)
    assertEquals(t, d)
  }

  test("slim2: two-byte patterns") {
    val (t, d) = both(Seq("ab", "cd", "ef"), "abcdefabcdef" * 20)
    assertEquals(t, d)
  }

  test("slim3: three-byte and longer patterns") {
    val (t, d) = both(
      Seq("foo", "bar", "baz", "qux", "foobar"),
      ("foo" + "bar" + "baz" + "qux" + "foobar") * 30
    )
    assertEquals(t, d)
  }

  test("chunk boundary: match spanning 16-byte boundary") {
    val hay = ("x" * 14 + "needle" + "y" * 100)
    val (t, d) = both(Seq("needle"), hay)
    assertEquals(t, d)
    assert(t.nonEmpty)
  }

  test("chunk boundary: match at exact boundary") {
    for (offset <- 0 until 32) {
      val hay = ("x" * offset + "abc" + "y" * 100)
      val (t, d) = both(Seq("abc"), hay)
      assertEquals(t, d, s"offset=$offset")
    }
  }

  test("many patterns force bucket collisions") {
    val patterns = ('a' to 'z').map(c => s"${c}${c}${c}").toSeq
    val hay = patterns.mkString("") * 5
    val (t, d) = both(patterns, hay)
    assertEquals(t, d)
  }

  test("teddy disabled for >128 patterns") {
    val patterns = (0 until 200).map(i => f"p$i%03d")
    val ac = AhoCorasick(patterns)
    assert(ac.teddy.isEmpty)
  }

  test("teddy disabled when all share fingerprint") {
    val patterns = (0 until 8).map(i => s"xxx$i")
    val ac = AhoCorasick(patterns)
    assert(ac.teddy.isEmpty)
  }

  test("fat teddy enabled when slim overflows") {
    val patterns = (0 until 48).map(i => f"${('a' + i % 26).toChar}$i%02d")
    val ac = AhoCorasick(patterns)
    assert(ac.teddy.isDefined)
    assert(ac.teddy.get.isInstanceOf[tineola.teddy.TeddyFat])
  }

  test("fat teddy agrees with DAT") {
    val patterns = (0 until 48).map(i => f"${('a' + i % 26).toChar}$i%02d")
    val hay = (patterns.mkString + "noise") * 30
    val (t, d) = both(patterns, hay)
    assertEquals(t, d)
  }

  test("fat teddy chunk boundaries") {
    val patterns = (0 until 48).map(i => f"${('a' + i % 26).toChar}$i%02d")
    for (offset <- 0 until 32) {
      val hay = "z" * offset + patterns(0) + "z" * 100
      val (t, d) = both(patterns, hay)
      assertEquals(t, d, s"offset=$offset")
    }
  }

  test("teddy enabled for diverse small set") {
    val patterns = Seq("abc", "def", "ghi", "jkl", "mno", "pqr", "stu", "vwx")
    val ac = AhoCorasick(patterns)
    assert(ac.teddy.isDefined)
  }

  test("species 128 and 256 agree with DAT") {
    val patterns = Seq("alpha", "beta", "gamma", "delta", "epsilon", "zeta")
    val hay = (patterns.mkString + "noise") * 40
    val ps = patterns.map(_.getBytes(UTF_8))

    def collect(species: jdk.incubator.vector.VectorSpecies[java.lang.Byte]) =
      AhoCorasick.builder
        .addAll(ps)
        .teddySpecies(species)
        .build()
        .findAll(hay)
        .map(m => (m.pattern, m.start, m.end))
        .toSet

    val dat = AhoCorasick.builder
      .addAll(ps)
      .enableTeddy(false)
      .build()
      .findAll(hay)
      .map(m => (m.pattern, m.start, m.end))
      .toSet

    assertEquals(collect(ByteVector.SPECIES_128), dat, "128-bit")
    assertEquals(collect(ByteVector.SPECIES_256), dat, "256-bit")
  }

  test("tail handled by DAT") {
    val hay = "x" * 100 + "end"
    val (t, d) = both(Seq("end"), hay)
    assertEquals(t, d)
    assert(t.nonEmpty)
  }
}
