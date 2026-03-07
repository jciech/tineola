package tineola.teddy

import jdk.incubator.vector.{ByteVector, VectorOperators}

import tineola.Match
import tineola.automaton.DoubleArrayTrie

private[teddy] final class TeddySlim2(m: Masks, dat: DoubleArrayTrie) extends Teddy {

  private val S = ByteVector.SPECIES_128
  private val lane = S.length()
  private val stride = lane - 1
  private val nib = ByteVector.broadcast(S, 0x0f)
  private val lo0 = ByteVector.fromArray(S, m.lo(0), 0)
  private val hi0 = ByteVector.fromArray(S, m.hi(0), 0)
  private val lo1 = ByteVector.fromArray(S, m.lo(1), 0)
  private val hi1 = ByteVector.fromArray(S, m.hi(1), 0)
  private val shl1 = S.iotaShuffle(1, 1, true)
  private val buckets = m.buckets
  private val pats = m.patterns
  private val plens = dat.patternLengths

  private val maxPatLen = plens.max

  def minHaystackLen: Int = lane + maxPatLen

  def findAll(h: Array[Byte], from: Int, to: Int, out: Match => Unit): Unit = {
    val bound = to - lane
    var i = from
    while (i <= bound) {
      val chunk = ByteVector.fromArray(S, h, i)
      val lo = chunk.and(nib)
      val hi = chunk.lanewise(VectorOperators.LSHR, 4).and(nib)
      val c0 = lo.selectFrom(lo0).and(hi.selectFrom(hi0))
      val c1 = lo.selectFrom(lo1).and(hi.selectFrom(hi1))
      val cand = c0.and(c1.rearrange(shl1))
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
    val buf = new Array[Byte](lane)
    cand.intoArray(buf, 0)
    var off = 0
    while (off < stride) {
      var bits = buf(off) & 0xff
      while (bits != 0) {
        val bucket = Integer.numberOfTrailingZeros(bits)
        bits &= bits - 1
        val ps = buckets(bucket)
        var j = 0
        while (j < ps.length) {
          val pid = ps(j)
          val plen = plens(pid)
          val pos = base + off
          if (pos + plen <= end && matches(h, pos, pats(pid), plen))
            out(Match(pid, pos, pos + plen))
          j += 1
        }
      }
      off += 1
    }
  }

  @inline
  private def matches(h: Array[Byte], pos: Int, p: Array[Byte], len: Int): Boolean = {
    var i = 0
    while (i < len) { if (h(pos + i) != p(i)) return false; i += 1 }
    true
  }
}
