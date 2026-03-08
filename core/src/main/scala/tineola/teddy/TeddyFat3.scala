package tineola.teddy

import jdk.incubator.vector.{ByteVector, VectorOperators}

import tineola.Match
import tineola.automaton.DoubleArrayTrie

private[teddy] final class TeddyFat3(m: Masks, dat: DoubleArrayTrie) extends TeddyFat(m, dat) {

  private val lo0 = ByteVector.fromArray(S256, m.lo(0), 0)
  private val hi0 = ByteVector.fromArray(S256, m.hi(0), 0)
  private val lo1 = ByteVector.fromArray(S256, m.lo(1), 0)
  private val hi1 = ByteVector.fromArray(S256, m.hi(1), 0)
  private val lo2 = ByteVector.fromArray(S256, m.lo(2), 0)
  private val hi2 = ByteVector.fromArray(S256, m.hi(2), 0)
  private val shl1 = halfShift(1)
  private val shl2 = halfShift(2)
  private val stride = Chunk - 2

  final def findAll(h: Array[Byte], from: Int, to: Int, out: Match => Unit): Unit = {
    val bound = to - 32
    var i = from
    while (i <= bound) {
      val chunk = dupChunk(h, i)
      val lo = nibLo(chunk)
      val hi = nibHi(chunk)
      val c0 = lo.selectFrom(lo0).and(hi.selectFrom(hi0))
      val c1 = lo.selectFrom(lo1).and(hi.selectFrom(hi1))
      val c2 = lo.selectFrom(lo2).and(hi.selectFrom(hi2))
      val cand = c0.and(c1.rearrange(shl1)).and(c2.rearrange(shl2))
      if (cand.compare(VectorOperators.NE, 0.toByte).anyTrue())
        verify(h, i, to, stride, cand, out)
      i += stride
    }
    dat.findAll(h, i, to, out)
  }
}
