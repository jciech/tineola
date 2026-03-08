package tineola.teddy

import jdk.incubator.vector.{ByteVector, VectorSpecies}

import tineola.Match
import tineola.automaton.DoubleArrayTrie

private[tineola] trait Teddy {
  def minHaystackLen: Int
  def findAll(haystack: Array[Byte], from: Int, to: Int, out: Match => Unit): Unit
}

private[tineola] object Teddy {

  val MaxPatterns = 128
  val MaxSlimBucketSize = 4
  val MaxFatBucketSize = 3

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

    val slim = Masks.buildSlim(patterns, n)
    if (slim.maxBucketSize <= MaxSlimBucketSize)
      return Some(n match {
        case 1 => new TeddySlim1(species, slim, dat)
        case 2 => new TeddySlim2(species, slim, dat)
        case 3 => new TeddySlim3(species, slim, dat)
      })

    if (species.length() < 32) return None
    val fat = Masks.buildFat(patterns, n)
    if (fat.maxBucketSize <= MaxFatBucketSize)
      return Some(n match {
        case 1 => new TeddyFat1(fat, dat)
        case 2 => new TeddyFat2(fat, dat)
        case 3 => new TeddyFat3(fat, dat)
      })

    None
  }
}
