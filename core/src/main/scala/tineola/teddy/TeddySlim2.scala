package tineola.teddy

import jdk.incubator.vector.{ByteVector, VectorSpecies}

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

  protected def fingerprintLen: Int = 2

  protected def candidates(lo: ByteVector, hi: ByteVector): ByteVector = {
    val c0 = lo.selectFrom(lo0).and(hi.selectFrom(hi0))
    val c1 = lo.selectFrom(lo1).and(hi.selectFrom(hi1))
    c0.and(c1.rearrange(shl1))
  }
}
