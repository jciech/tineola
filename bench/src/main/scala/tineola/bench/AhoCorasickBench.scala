package tineola.bench

import java.util.concurrent.TimeUnit
import java.util.TreeMap

import scala.compiletime.uninitialized

import org.openjdk.jmh.annotations._
import org.openjdk.jmh.infra.Blackhole

import com.hankcs.algorithm.AhoCorasickDoubleArrayTrie
import com.hankcs.algorithm.AhoCorasickDoubleArrayTrie.IHit
import org.ahocorasick.trie.Trie

import tineola.AhoCorasick

@State(Scope.Benchmark)
@BenchmarkMode(Array(Mode.Throughput))
@OutputTimeUnit(TimeUnit.MILLISECONDS)
@Fork(value = 1, jvmArgsAppend = Array("--add-modules=jdk.incubator.vector"))
@Warmup(iterations = 5, time = 1)
@Measurement(iterations = 5, time = 1)
class AhoCorasickBench {

  @Param(Array("8", "32", "128", "512"))
  var numPatterns: Int = uninitialized

  @Param(Array("65536"))
  var haystackLen: Int = uninitialized

  private var patterns: Array[String] = uninitialized
  private var haystack: Array[Byte] = uninitialized
  private var haystackStr: String = uninitialized
  private var tineolaTeddy: AhoCorasick = uninitialized
  private var tineolaDat: AhoCorasick = uninitialized
  private var hankcs: AhoCorasickDoubleArrayTrie[String] = uninitialized
  private var robertBor: Trie = uninitialized

  @Setup
  def setup(): Unit = {
    patterns = Fixtures.randomPatterns(numPatterns, 3, 8)
    haystack = Fixtures.seededHaystack(haystackLen, patterns, 0.01)
    haystackStr = new String(haystack)

    tineolaTeddy = AhoCorasick(patterns.toIndexedSeq)
    tineolaDat = AhoCorasick.builder
      .addAll(patterns.map(_.getBytes).toIndexedSeq)
      .enableTeddy(false)
      .build()

    val map = new TreeMap[String, String]()
    patterns.foreach(p => map.put(p, p))
    hankcs = new AhoCorasickDoubleArrayTrie[String]()
    hankcs.build(map)

    robertBor = Trie.builder().addKeywords(patterns*).build()
  }

  @Benchmark
  def tineola_teddy(bh: Blackhole): Unit = {
    val it = tineolaTeddy.findAll(haystack)
    while (it.hasNext) bh.consume(it.next())
  }

  @Benchmark
  def tineola_dat(bh: Blackhole): Unit = {
    val it = tineolaDat.findAll(haystack)
    while (it.hasNext) bh.consume(it.next())
  }

  @Benchmark
  def hankcs_dat(bh: Blackhole): Unit = {
    val hit: IHit[String] = (_, _, v) => bh.consume(v)
    hankcs.parseText(haystackStr, hit)
  }

  @Benchmark
  def robert_bor(bh: Blackhole): Unit = {
    val emits = robertBor.parseText(haystackStr)
    val it = emits.iterator()
    while (it.hasNext) bh.consume(it.next())
  }
}
