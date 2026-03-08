package tineola.teddy

import jdk.incubator.vector.{ByteVector, VectorOperators, VectorSpecies}

import tineola.Match
import tineola.automaton.DoubleArrayTrie

private[teddy] abstract class TeddySlim(
    protected final val S: VectorSpecies[java.lang.Byte],
    m: Masks,
    dat: DoubleArrayTrie
) extends Teddy {

  protected final val lane = S.length()
  private val numLongs = lane >>> 3
  protected final val nib = ByteVector.broadcast(S, 0x0f)
  private val buckets = m.buckets
  private val pats = m.patterns
  private val plens = dat.patternLengths
  private val maxPatLen = plens.max

  protected def fingerprintLen: Int
  protected def candidates(lo: ByteVector, hi: ByteVector): ByteVector

  private val stride = lane - fingerprintLen + 1

  final def minHaystackLen: Int = lane + maxPatLen

  final def findAll(h: Array[Byte], from: Int, to: Int, out: Match => Unit): Unit = {
    val bound = to - lane
    var i = from
    while (i <= bound) {
      val chunk = ByteVector.fromArray(S, h, i)
      val lo = chunk.and(nib)
      val hi = chunk.lanewise(VectorOperators.LSHR, 4).and(nib)
      val cand = candidates(lo, hi)
      if (cand.compare(VectorOperators.NE, 0.toByte).anyTrue())
        verify(h, i, to, cand, out)
      i += stride
    }
    dat.findAll(h, i, to, out)
  }

  private def verify(
      h: Array[Byte],
      base: Int,
      end: Int,
      cand: ByteVector,
      out: Match => Unit
  ): Unit = {
    val longs = cand.reinterpretAsLongs()
    var k = 0
    while (k < numLongs) {
      var bits = longs.lane(k)
      while (bits != 0L) {
        val tz = java.lang.Long.numberOfTrailingZeros(bits)
        bits &= bits - 1L
        val off = (k << 3) + (tz >>> 3)
        if (off < stride) {
          val bucket = tz & 7
          val ps = buckets(bucket)
          val pos = base + off
          var j = 0
          while (j < ps.length) {
            val pid = ps(j)
            val plen = plens(pid)
            if (pos + plen <= end && matches(h, pos, pats(pid), plen))
              out(Match(pid, pos, pos + plen))
            j += 1
          }
        }
      }
      k += 1
    }
  }

  @inline
  private def matches(h: Array[Byte], pos: Int, p: Array[Byte], len: Int): Boolean = {
    var i = 0
    while (i < len) { if (h(pos + i) != p(i)) return false; i += 1 }
    true
  }
}
