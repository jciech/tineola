package tineola

import java.io.ByteArrayOutputStream
import java.nio.charset.StandardCharsets.UTF_8
import java.util.{Arrays, Comparator, Objects}

import scala.collection.AbstractIterator

import jdk.incubator.vector.VectorSpecies

import tineola.automaton.{DoubleArrayTrie, TrieBuilder}
import tineola.teddy.Teddy

final class AhoCorasick private (
    private[tineola] val automaton: DoubleArrayTrie,
    private[tineola] val teddy: Option[Teddy],
    val options: AhoCorasick.Options
) {

  private val maxLen = automaton.patternLengths.foldLeft(0)(math.max)
  private val block = math.max(1 << 16, maxLen)

  def numPatterns: Int = automaton.numPatterns

  def isMatch(text: String): Boolean =
    isMatch(text.getBytes(UTF_8))

  def isMatch(bytes: Array[Byte]): Boolean =
    isMatch(bytes, 0, bytes.length)

  def isMatch(bytes: Array[Byte], from: Int, until: Int): Boolean = {
    Objects.checkFromToIndex(from, until, bytes.length)
    var found = false
    scan(bytes, from, until) { _ => found = true; false }
    found
  }

  def findFirst(text: String): Option[Match] = {
    val bytes = text.getBytes(UTF_8)
    Option(leftmost(bytes, 0, bytes.length)).map(inChars(text, bytes))
  }

  def findFirst(bytes: Array[Byte]): Option[Match] =
    findFirst(bytes, 0, bytes.length)

  def findFirst(bytes: Array[Byte], from: Int, until: Int): Option[Match] = {
    Objects.checkFromToIndex(from, until, bytes.length)
    Option(leftmost(bytes, from, until))
  }

  def findAll(text: String): Iterator[Match] = {
    val bytes = text.getBytes(UTF_8)
    new Leftmost(bytes, 0, bytes.length).map(inChars(text, bytes))
  }

  def findAll(bytes: Array[Byte]): Iterator[Match] =
    findAll(bytes, 0, bytes.length)

  def findAll(bytes: Array[Byte], from: Int, until: Int): Iterator[Match] = {
    Objects.checkFromToIndex(from, until, bytes.length)
    new Leftmost(bytes, from, until)
  }

  def findOverlapping(text: String): Iterator[Match] = {
    val bytes = text.getBytes(UTF_8)
    new Overlapping(bytes, 0, bytes.length).map(inChars(text, bytes))
  }

  def findOverlapping(bytes: Array[Byte]): Iterator[Match] =
    findOverlapping(bytes, 0, bytes.length)

  def findOverlapping(bytes: Array[Byte], from: Int, until: Int): Iterator[Match] = {
    Objects.checkFromToIndex(from, until, bytes.length)
    new Overlapping(bytes, from, until)
  }

  def replaceAll(text: String)(f: Match => String): String = {
    val sb = new java.lang.StringBuilder(text.length)
    var last = 0
    findAll(text).foreach { m =>
      sb.append(text, last, m.start).append(f(m))
      last = m.end
    }
    sb.append(text, last, text.length).toString
  }

  def replaceAll(bytes: Array[Byte])(f: Match => Array[Byte]): Array[Byte] = {
    val out = new ByteArrayOutputStream(bytes.length)
    var last = 0
    findAll(bytes).foreach { m =>
      out.write(bytes, last, m.start - last)
      out.writeBytes(f(m))
      last = m.end
    }
    out.write(bytes, last, bytes.length - last)
    out.toByteArray
  }

  override def toString: String = {
    val engine = teddy.fold("scalar")(_.getClass.getSimpleName)
    s"AhoCorasick($numPatterns patterns, ${options.matchKind}, $engine)"
  }

  private def inChars(text: String, bytes: Array[Byte]): Match => Match =
    if (bytes.length == text.length) identity else new AhoCorasick.CharOffsets(bytes)

  private def leftmost(h: Array[Byte], from: Int, until: Int): Match = {
    var first: Match = null
    scan(h, from, until) { m => first = m; false }
    if (first == null) null
    else {
      var best: Match = null
      val lo = math.max(from, first.end - maxLen)
      val hi = math.min(until, first.start + maxLen)
      automaton.scan(h, lo, hi, m => { if (best == null || prefers(m, best)) best = m; true })
      best
    }
  }

  private def prefers(a: Match, b: Match): Boolean =
    if (a.start != b.start) a.start < b.start
    else
      options.matchKind match {
        case MatchKind.LeftmostFirst   => a.pattern < b.pattern
        case MatchKind.LeftmostLongest => a.end > b.end || (a.end == b.end && a.pattern < b.pattern)
      }

  private def scan(h: Array[Byte], from: Int, until: Int)(out: Match => Boolean): Unit =
    teddy match {
      case Some(t) if until - from >= t.minHaystackLen =>
        t.scan(h, from, until, out)
      case _ =>
        automaton.scan(h, from, until, out)
    }

  private final class Leftmost(h: Array[Byte], from: Int, until: Int)
      extends AbstractIterator[Match] {
    private var pos = from
    private var pending: Match = null

    def hasNext: Boolean = {
      if (pending == null && pos < until) {
        pending = leftmost(h, pos, until)
        pos = if (pending == null) until else pending.end
      }
      pending != null
    }

    def next(): Match = {
      if (!hasNext) throw new NoSuchElementException
      val m = pending
      pending = null
      m
    }
  }

  private final class Overlapping(h: Array[Byte], from: Int, until: Int)
      extends AbstractIterator[Match] {
    private var buf = new Array[Match](16)
    private var i = 0
    private var n = 0
    private var pos = from

    def hasNext: Boolean = {
      while (i == n && pos < until) fill()
      i < n
    }

    def next(): Match = {
      if (!hasNext) throw new NoSuchElementException
      val m = buf(i)
      i += 1
      m
    }

    private def fill(): Unit = {
      val end = if (until - pos <= block) until else pos + block
      val scanEnd = if (until - end < maxLen) until else end + maxLen - 1
      i = 0
      n = 0
      scan(h, pos, scanEnd) { m =>
        if (m.start < end) {
          if (n == buf.length) buf = Arrays.copyOf(buf, n * 2)
          buf(n) = m
          n += 1
        }
        true
      }
      Arrays.sort(buf, 0, n, AhoCorasick.byPosition)
      pos = end
    }
  }
}

object AhoCorasick {

  final case class Options(matchKind: MatchKind = MatchKind.LeftmostFirst, simd: Boolean = true)

  def apply(patterns: String*): AhoCorasick =
    apply(patterns, Options())

  def apply(patterns: IterableOnce[String], options: Options = Options()): AhoCorasick =
    create(patterns.iterator.map(_.getBytes(UTF_8)).toArray, options, None)

  def fromBytes(patterns: IterableOnce[Array[Byte]], options: Options = Options()): AhoCorasick =
    create(patterns.iterator.map(_.clone()).toArray, options, None)

  private[tineola] def build(
      patterns: Array[Array[Byte]],
      options: Options,
      species: VectorSpecies[java.lang.Byte]
  ): AhoCorasick =
    create(patterns, options, Some(species))

  private def create(
      patterns: Array[Array[Byte]],
      options: Options,
      species: Option[VectorSpecies[java.lang.Byte]]
  ): AhoCorasick = {
    val tb = new TrieBuilder
    patterns.foreach(tb.addPattern)
    val dat = tb.build()
    val teddy =
      if (!options.simd || !simdAvailable) None
      else
        species match {
          case Some(s) => Teddy.tryBuild(patterns, dat, s)
          case None    => Teddy.tryBuild(patterns, dat)
        }
    new AhoCorasick(dat, teddy, options)
  }

  private lazy val simdAvailable: Boolean =
    try Teddy.DefaultSpecies ne null
    catch { case _: LinkageError => false }

  private val byPosition: Comparator[Match] = (a, b) =>
    if (a.start != b.start) Integer.compare(a.start, b.start)
    else if (a.end != b.end) Integer.compare(a.end, b.end)
    else Integer.compare(a.pattern, b.pattern)

  private final class CharOffsets(bytes: Array[Byte]) extends (Match => Match) {
    private var b = 0
    private var c = 0

    def apply(m: Match): Match = {
      while (b < m.start) { c += units(bytes(b)); b += 1 }
      var end = c
      var k = m.start
      while (k < m.end) { end += units(bytes(k)); k += 1 }
      Match(m.pattern, c, end)
    }
  }

  private def units(b: Byte): Int =
    if ((b & 0xc0) == 0x80) 0 else if ((b & 0xf8) == 0xf0) 2 else 1
}
