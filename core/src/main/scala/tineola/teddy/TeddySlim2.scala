package tineola.teddy

import jdk.incubator.vector.{ByteVector, VectorOperators, VectorSpecies}

import tineola.Match
import tineola.automaton.DoubleArrayTrie

private[teddy] final class TeddySlim2(
    S: VectorSpecies[java.lang.Byte],
    m: Masks,
    dat: DoubleArrayTrie
) extends TeddySlim(S, m, dat) {

  private val lo0 = ByteVector.fromArray(S, Masks.tile(m.lo(0), lane), 0)
  private val hi0 = ByteVector.fromArray(S, Masks.tile(m.hi(0), lane), 0)
  private val lo1 = ByteVector.fromArray(S, Masks.tile(m.lo(1), lane), 0)
  private val hi1 = ByteVector.fromArray(S, Masks.tile(m.hi(1), lane), 0)
  private val shl1 = S.iotaShuffle(1, 1, true)
  private val stride = lane - 1

  final def findAll(h: Array[Byte], from: Int, to: Int, out: Match => Unit): Unit = {
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
        verify(h, i, to, stride, cand, out)
      i += stride
    }
    dat.findAll(h, i, to, out)
  }
}
