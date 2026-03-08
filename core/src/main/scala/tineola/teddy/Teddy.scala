package tineola.teddy

import jdk.incubator.vector.{ByteVector, VectorSpecies}

import tineola.Match
import tineola.automaton.DoubleArrayTrie

private[tineola] trait Teddy {
  def minHaystackLen: Int
  def findAll(haystack: Array[Byte], from: Int, to: Int, out: Match => Unit): Unit
}

private[tineola] object Teddy {

  val MaxPatterns = 64
  val MaxBucketSize = 4

  val DefaultSpecies: VectorSpecies[java.lang.Byte] = {
    val pref = ByteVector.SPECIES_PREFERRED
    if (pref.length() > 32) ByteVector.SPECIES_256
    else if (pref.length() >= 16) pref
    else ByteVector.SPECIES_128
  }

  def tryBuild(
      patterns: Array[Array[Byte]],
      dat: DoubleArrayTrie,
      species: VectorSpecies[java.lang.Byte] = DefaultSpecies
  ): Option[Teddy] = {
    if (patterns.isEmpty || patterns.length > MaxPatterns) return None
    if (species.length() < 16) return None
    val minLen = patterns.iterator.map(_.length).min
    if (minLen < 1) return None
    val n = if (minLen >= 3) 3 else if (minLen >= 2) 2 else 1
    val masks = Masks.build(patterns, n)
    if (masks.buckets.iterator.map(_.length).max > MaxBucketSize) return None
    Some(n match {
      case 1 => new TeddySlim1(species, masks, dat)
      case 2 => new TeddySlim2(species, masks, dat)
      case 3 => new TeddySlim3(species, masks, dat)
    })
  }
}
