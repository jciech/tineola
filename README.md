# tineola

simd-accelerated multi-pattern string matching for scala.

named after *tineola bisselliella*, the common clothes moth. small, fast, finds things in fabric.

## what it does

finds all occurrences of a set of patterns in a haystack. combines two engines:

- a **teddy simd prefilter** (via `jdk.incubator.vector`) for small pattern sets. scans 16 bytes at a time using the pshufb nibble-lookup trick from hyperscan/rust's `aho-corasick`
- a **double-array trie aho-corasick** automaton as scalar fallback. works for any pattern count, handles tails and large pattern sets

the teddy path auto-enables for ≤128 patterns and haystacks long enough to fill a simd lane. everything else routes to the automaton. results are identical either way.

## usage

```scala
import tineola.AhoCorasick

val ac = AhoCorasick(Seq("foo", "bar", "baz"))

ac.findAll("foobarbaz").toList
// List(Match(0, 0, 3), Match(1, 3, 6), Match(2, 6, 9))

ac.findFirst("xxbarxx")
// Some(Match(1, 2, 5))
```

byte-oriented api for already-encoded data:

```scala
val ac = AhoCorasick.fromBytes(Seq("needle".getBytes))
ac.findAll(haystackBytes)
```

disable the simd path for benchmarking or debugging:

```scala
AhoCorasick.builder
  .addPattern("foo")
  .enableTeddy(false)
  .build()
```

## requirements

- jdk 21 or later
- `--add-modules jdk.incubator.vector` at runtime (and at compile time if building from source)

jdk 24+ is recommended. `selectFrom` got index-wrap semantics ([jep 489](https://openjdk.org/jeps/489)) which lets the jit emit raw `vpshufb` without bounds checks.

## benchmarks

throughput, 64kb haystack, random lowercase ascii with ~1% match density.

```
sbt "bench/Jmh/run -i 10 -wi 10 -f 3"
```

single-iteration smoke numbers (jdk 21, 8 patterns. run the full bench for real data):

| impl | ops/ms |
|---|---|
| tineola (teddy) | 40.93 |
| hankcs/AhoCorasickDoubleArrayTrie | 3.34 |
| tineola (dat only) | 2.67 |
| robert-bor/aho-corasick | 1.57 |

teddy's advantage shrinks as pattern count grows; above ~128 patterns the automaton wins and teddy is disabled automatically.

## installing

cross-published for scala 2.13 and scala 3.

```scala
libraryDependencies += "io.github.jciech" %% "tineola" % "<version>"
```

## references

- [teddy algorithm writeup](https://github.com/BurntSushi/aho-corasick/blob/master/src/packed/teddy/README.md) (burntSushi/aho-corasick)
- [engineering faster double-array aho-corasick automata](https://arxiv.org/abs/2207.13870) (daachorse paper)
- [jdk vector api](https://openjdk.org/jeps/529)
