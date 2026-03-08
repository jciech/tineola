package tineola

import java.nio.charset.StandardCharsets.UTF_8
import scala.collection.mutable.ArrayBuffer

import jdk.incubator.vector.VectorSpecies

import tineola.automaton.{DoubleArrayTrie, TrieBuilder}
import tineola.teddy.Teddy

final class AhoCorasick private (
    private[tineola] val automaton: DoubleArrayTrie,
    private[tineola] val teddy: Option[Teddy]
) {

  def numPatterns: Int = automaton.numPatterns

  def findAll(haystack: Array[Byte]): Iterator[Match] =
    findAll(haystack, 0, haystack.length)

  def findAll(haystack: Array[Byte], from: Int, to: Int): Iterator[Match] = {
    val buf = ArrayBuffer.empty[Match]
    teddy match {
      case Some(t) if to - from >= t.minHaystackLen =>
        t.findAll(haystack, from, to, buf += _)
      case _ =>
        automaton.findAll(haystack, from, to, buf += _)
    }
    buf.iterator
  }

  def findAll(haystack: String): Iterator[Match] =
    findAll(haystack.getBytes(UTF_8))

  def findFirst(haystack: Array[Byte]): Option[Match] = {
    var result: Option[Match] = None
    val cb: Match => Unit = m => if (result.isEmpty) result = Some(m)
    teddy match {
      case Some(t) if haystack.length >= t.minHaystackLen =>
        t.findAll(haystack, 0, haystack.length, cb)
      case _ =>
        automaton.findAll(haystack, 0, haystack.length, cb)
    }
    result
  }

  def findFirst(haystack: String): Option[Match] =
    findFirst(haystack.getBytes(UTF_8))
}

object AhoCorasick {

  def apply(patterns: Iterable[String]): AhoCorasick =
    builder.addAll(patterns.map(_.getBytes(UTF_8))).build()

  def fromBytes(patterns: Iterable[Array[Byte]]): AhoCorasick =
    builder.addAll(patterns).build()

  def builder: Builder = new Builder

  final class Builder private[AhoCorasick] () {
    private val tb = new TrieBuilder
    private val patterns = ArrayBuffer.empty[Array[Byte]]
    private var useTeddy = true
    private var species: VectorSpecies[java.lang.Byte] = Teddy.DefaultSpecies

    def addPattern(p: Array[Byte]): this.type = {
      tb.addPattern(p); patterns += p; this
    }

    def addPattern(p: String): this.type =
      addPattern(p.getBytes(UTF_8))

    def addAll(ps: Iterable[Array[Byte]]): this.type = {
      ps.foreach(addPattern); this
    }

    def enableTeddy(on: Boolean): this.type = { useTeddy = on; this }

    def teddySpecies(s: VectorSpecies[java.lang.Byte]): this.type = { species = s; this }

    def build(): AhoCorasick = {
      val dat = tb.build()
      val td = if (useTeddy) Teddy.tryBuild(patterns.toArray, dat, species) else None
      new AhoCorasick(dat, td)
    }
  }
}
