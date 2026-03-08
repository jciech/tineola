package tineola.teddy

import jdk.incubator.vector.{ByteVector, VectorSpecies}

import tineola.automaton.DoubleArrayTrie

private[teddy] final class TeddySlim1(
    S: VectorSpecies[java.lang.Byte],
    m: Masks,
    dat: DoubleArrayTrie
) extends TeddySlim(S, m, dat) {

  private val loMask = ByteVector.fromArray(S, Masks.tile(m.lo(0), lane), 0)
  private val hiMask = ByteVector.fromArray(S, Masks.tile(m.hi(0), lane), 0)

  protected def fingerprintLen: Int = 1

  protected def candidates(lo: ByteVector, hi: ByteVector): ByteVector =
    lo.selectFrom(loMask).and(hi.selectFrom(hiMask))
}
