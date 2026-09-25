package tineola

import org.scalacheck.Gen
import org.scalacheck.Prop.forAllNoShrink

import java.nio.charset.StandardCharsets.UTF_8
import jdk.incubator.vector.{ByteVector, VectorSpecies}

class PropertySuite extends munit.ScalaCheckSuite {

  override def scalaCheckTestParameters =
    super.scalaCheckTestParameters.withMinSuccessfulTests(200)

  private val alphabet = "abcdefghijklmnopqrstuvwxyz0123456789"

  private val patternGen: Gen[String] =
    for {
      len <- Gen.choose(1, 8)
      chars <- Gen.listOfN(len, Gen.oneOf(alphabet))
    } yield chars.mkString

  private val patternsGen: Gen[List[String]] =
    for {
      n <- Gen.choose(1, 30)
      ps <- Gen.listOfN(n, patternGen)
    } yield ps.distinct

  private val manyPatternsGen: Gen[List[String]] =
    for {
      n <- Gen.choose(30, 80)
      ps <- Gen.listOfN(n, patternGen)
    } yield ps.distinct

  private val haystackGen: Gen[String] =
    for {
      len <- Gen.choose(0, 500)
      chars <- Gen.listOfN(len, Gen.oneOf(alphabet))
    } yield chars.mkString

  private val unicodeUnit: Gen[String] = Gen.oneOf("a", "b", "é", "ß", "日", "😀")

  private val unicodePatternsGen: Gen[List[String]] =
    for {
      n <- Gen.choose(1, 10)
      ps <- Gen.listOfN(n, Gen.choose(1, 4).flatMap(Gen.listOfN(_, unicodeUnit)).map(_.mkString))
    } yield ps.distinct

  private val unicodeHaystackGen: Gen[String] =
    Gen.choose(0, 200).flatMap(Gen.listOfN(_, unicodeUnit)).map(_.mkString)

  private val kinds: List[MatchKind] = List(MatchKind.LeftmostFirst, MatchKind.LeftmostLongest)

  property("teddy (128) agrees with DAT") {
    forAllNoShrink(patternsGen, haystackGen) { (patterns, hay) =>
      parity(patterns, hay, ByteVector.SPECIES_128)
    }
  }

  property("teddy (256) agrees with DAT") {
    forAllNoShrink(patternsGen, haystackGen) { (patterns, hay) =>
      parity(patterns, hay, ByteVector.SPECIES_256)
    }
  }

  property("fat teddy agrees with DAT") {
    forAllNoShrink(manyPatternsGen, haystackGen) { (patterns, hay) =>
      parity(patterns, hay, ByteVector.SPECIES_256)
    }
  }

  property("findOverlapping matches naive search") {
    forAllNoShrink(patternsGen, haystackGen) { (patterns, hay) =>
      AhoCorasick(patterns).findOverlapping(hay).toList == naiveOverlapping(patterns, hay)
    }
  }

  property("findAll and findFirst match naive leftmost search") {
    forAllNoShrink(patternsGen, haystackGen) { (patterns, hay) =>
      kinds.forall { kind =>
        val ac = AhoCorasick(patterns, AhoCorasick.Options(matchKind = kind))
        val expected = naiveLeftmost(patterns, hay, kind)
        ac.findAll(hay).toList == expected && ac.findFirst(hay) == expected.headOption
      }
    }
  }

  property("string offsets are char indices") {
    forAllNoShrink(unicodePatternsGen, unicodeHaystackGen) { (patterns, hay) =>
      val ac = AhoCorasick(patterns)
      ac.findOverlapping(hay).toList == naiveOverlapping(patterns, hay) &&
      ac.findAll(hay).toList == naiveLeftmost(patterns, hay, MatchKind.LeftmostFirst)
    }
  }

  private def parity(
      patterns: List[String],
      hay: String,
      species: VectorSpecies[java.lang.Byte]
  ): Boolean = {
    val ps = patterns.map(_.getBytes(UTF_8)).toArray
    val h = hay.getBytes(UTF_8)
    kinds.forall { kind =>
      val options = AhoCorasick.Options(matchKind = kind)
      val teddy = AhoCorasick.build(ps, options, species)
      val dat = AhoCorasick.build(ps, options.copy(simd = false), species)
      teddy.findOverlapping(h).toList == dat.findOverlapping(h).toList &&
      teddy.findAll(h).toList == dat.findAll(h).toList &&
      teddy.findFirst(h) == dat.findFirst(h)
    }
  }

  private def naiveOverlapping(patterns: List[String], hay: String): List[Match] =
    (for {
      (p, i) <- patterns.zipWithIndex
      j <- 0 to (hay.length - p.length)
      if hay.startsWith(p, j)
    } yield Match(i, j, j + p.length)).sortBy(m => (m.start, m.end, m.pattern))

  private def naiveLeftmost(patterns: List[String], hay: String, kind: MatchKind): List[Match] = {
    def go(rest: List[Match]): List[Match] = rest match {
      case Nil    => Nil
      case m :: _ =>
        val here = rest.takeWhile(_.start == m.start)
        val pick = kind match {
          case MatchKind.LeftmostFirst   => here.minBy(_.pattern)
          case MatchKind.LeftmostLongest => here.maxBy(x => (x.end, -x.pattern))
        }
        pick :: go(rest.dropWhile(_.start < pick.end))
    }
    go(naiveOverlapping(patterns, hay))
  }
}
