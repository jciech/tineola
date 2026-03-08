package tineola.bench

import java.util.concurrent.TimeUnit

import scala.compiletime.uninitialized

import org.openjdk.jmh.annotations._
import org.openjdk.jmh.infra.Blackhole

import tineola.AhoCorasick

@State(Scope.Benchmark)
@BenchmarkMode(Array(Mode.Throughput))
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@Fork(value = 1, jvmArgsAppend = Array("--add-modules=jdk.incubator.vector"))
@Warmup(iterations = 5, time = 1)
@Measurement(iterations = 5, time = 1)
class FindFirstBench {

  private var ac: AhoCorasick = uninitialized
  private var hayEarly: Array[Byte] = uninitialized
  private var hayLate: Array[Byte] = uninitialized
  private var hayNone: Array[Byte] = uninitialized

  @Setup
  def setup(): Unit = {
    ac = AhoCorasick(Seq("needle", "target", "marker"))
    val tail = Fixtures.randomHaystack(1 << 20)
    hayEarly = "needle".getBytes ++ tail
    hayLate = tail ++ "needle".getBytes
    hayNone = tail
  }

  @Benchmark
  def early(bh: Blackhole): Unit = bh.consume(ac.findFirst(hayEarly))

  @Benchmark
  def late(bh: Blackhole): Unit = bh.consume(ac.findFirst(hayLate))

  @Benchmark
  def none(bh: Blackhole): Unit = bh.consume(ac.findFirst(hayNone))
}
