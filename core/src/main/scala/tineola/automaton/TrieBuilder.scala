package tineola.automaton

import scala.collection.mutable

private[tineola] final class TrieBuilder {

  private val goto = mutable.ArrayBuffer(mutable.HashMap.empty[Byte, Int])
  private val outputs = mutable.ArrayBuffer(mutable.ArrayBuffer.empty[Int])
  private val lengths = mutable.ArrayBuffer.empty[Int]

  def addPattern(bytes: Array[Byte]): Int = {
    require(bytes.nonEmpty, "patterns must be non-empty")
    val id = lengths.length
    lengths += bytes.length
    var state = 0
    var i = 0
    while (i < bytes.length) {
      val b = bytes(i)
      goto(state).get(b) match {
        case Some(next) => state = next
        case None       =>
          val next = goto.length
          goto += mutable.HashMap.empty[Byte, Int]
          outputs += mutable.ArrayBuffer.empty[Int]
          goto(state)(b) = next
          state = next
      }
      i += 1
    }
    outputs(state) += id
    id
  }

  def build(): DoubleArrayTrie = {
    val numStates = goto.length
    val fail = new Array[Int](numStates)
    val queue = new java.util.ArrayDeque[Int]()

    for ((_, s) <- goto(0)) {
      fail(s) = 0
      queue.addLast(s)
    }

    while (!queue.isEmpty) {
      val r = queue.pollFirst()
      for ((b, s) <- goto(r)) {
        queue.addLast(s)
        var f = fail(r)
        while (f != 0 && !goto(f).contains(b)) f = fail(f)
        fail(s) = goto(f).get(b).filter(_ != s).getOrElse(0)
        outputs(s) ++= outputs(fail(s))
      }
    }

    compile(fail)
  }

  private def compile(fail: Array[Int]): DoubleArrayTrie = {
    val numStates = goto.length
    val alphabet = 256

    var capacity = math.max(alphabet * 2, numStates * 2)
    var base = new Array[Int](capacity)
    var check = Array.fill(capacity)(-1)

    val stateToBase = new Array[Int](numStates)
    val stateToSlot = new Array[Int](numStates)
    stateToSlot(0) = 0
    check(0) = 0

    var nextFree = 1

    def ensureCapacity(needed: Int): Unit = {
      if (needed >= capacity) {
        val newCap = math.max(needed + 1, capacity * 2)
        val nb = new Array[Int](newCap)
        val nc = Array.fill(newCap)(-1)
        System.arraycopy(base, 0, nb, 0, capacity)
        System.arraycopy(check, 0, nc, 0, capacity)
        base = nb; check = nc; capacity = newCap
      }
    }

    def findBase(keys: Array[Int]): Int = {
      if (keys.isEmpty) return 0
      val minKey = keys(0)
      var b = math.max(nextFree - minKey, 1)
      while (true) {
        ensureCapacity(b + keys(keys.length - 1))
        var ok = true
        var i = 0
        while (ok && i < keys.length) {
          if (check(b + keys(i)) != -1) ok = false
          i += 1
        }
        if (ok) return b
        b += 1
      }
      b
    }

    val bfsOrder = {
      val order = mutable.ArrayBuffer(0)
      val q = new java.util.ArrayDeque[Int]()
      q.addLast(0)
      while (!q.isEmpty) {
        val s = q.pollFirst()
        for ((_, c) <- goto(s)) { order += c; q.addLast(c) }
      }
      order
    }

    for (s <- bfsOrder) {
      val children = goto(s).toArray.sortBy(_._1)
      val keys = children.map { case (b, _) => b & 0xff }
      val b = findBase(keys)
      stateToBase(s) = b
      val slot = stateToSlot(s)
      base(slot) = b
      for ((byte, child) <- children) {
        val k = byte & 0xff
        val dest = b + k
        ensureCapacity(dest)
        check(dest) = slot
        stateToSlot(child) = dest
        if (dest >= nextFree) nextFree = dest + 1
      }
    }

    val failSlot = new Array[Int](capacity)
    val outSlot = new Array[Array[Int]](capacity)
    for (s <- 0 until numStates) {
      val slot = stateToSlot(s)
      failSlot(slot) = stateToSlot(fail(s))
      val os = outputs(s)
      if (os.nonEmpty) outSlot(slot) = os.toArray
    }

    val lens = lengths.toArray
    new DoubleArrayTrie(
      base = java.util.Arrays.copyOf(base, nextFree),
      check = java.util.Arrays.copyOf(check, nextFree),
      fail = java.util.Arrays.copyOf(failSlot, nextFree),
      output = java.util.Arrays.copyOf(outSlot, nextFree),
      patternLengths = lens
    )
  }
}
