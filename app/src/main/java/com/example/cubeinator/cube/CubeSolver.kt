package com.example.cubeinator.cube

import java.util.ArrayDeque
import kotlin.math.max

sealed interface CubeValidationResult {
  data object Valid : CubeValidationResult
  data class Invalid(val reason: String, val details: String) : CubeValidationResult
}

data class SolveResult(
  val moves: List<CubeMove>,
  val statesAtEachStep: List<CubeState>,
  val solveTimeMs: Long,
) {
  val totalSteps: Int get() = moves.size
}

/**
 * High-speed Kociemba Two-Phase Solver + Physical Cube Validator.
 * Finds near-optimal / God's-number solutions (<= 21 moves, typically 16-20 moves)
 * in a few milliseconds on Android devices.
 */
object CubeSolver {

  // 8 Corners: URF(0), UFL(1), ULB(2), UBR(3), DFR(4), DLF(5), DBL(6), DRB(7)
  // Each triple is in clockwise order around the corner vertex starting from U or D.
  private val CORNER_FACELETS: Array<IntArray> = arrayOf(
    intArrayOf(8, 9, 20),   // 0: URF (U8, R0, F2)
    intArrayOf(6, 18, 38),  // 1: UFL (U6, F0, L2)
    intArrayOf(0, 36, 47),  // 2: ULB (U0, L0, B2)
    intArrayOf(2, 45, 11),  // 3: UBR (U2, B0, R2)
    intArrayOf(29, 26, 15), // 4: DFR (D2, F8, R6)
    intArrayOf(27, 44, 24), // 5: DLF (D0, L8, F6)
    intArrayOf(33, 53, 42), // 6: DBL (D6, B8, L6)
    intArrayOf(35, 17, 51), // 7: DRB (D8, R8, B6)
  )

  private val CORNER_COLORS: Array<Array<CubeColor>> = arrayOf(
    arrayOf(CubeColor.WHITE, CubeColor.RED, CubeColor.GREEN),     // 0: URF
    arrayOf(CubeColor.WHITE, CubeColor.GREEN, CubeColor.ORANGE),  // 1: UFL
    arrayOf(CubeColor.WHITE, CubeColor.ORANGE, CubeColor.BLUE),   // 2: ULB
    arrayOf(CubeColor.WHITE, CubeColor.BLUE, CubeColor.RED),      // 3: UBR
    arrayOf(CubeColor.YELLOW, CubeColor.GREEN, CubeColor.RED),    // 4: DFR
    arrayOf(CubeColor.YELLOW, CubeColor.ORANGE, CubeColor.GREEN), // 5: DLF
    arrayOf(CubeColor.YELLOW, CubeColor.BLUE, CubeColor.ORANGE),  // 6: DBL
    arrayOf(CubeColor.YELLOW, CubeColor.RED, CubeColor.BLUE),     // 7: DRB
  )

  // 12 Edges: UR(0), UF(1), UL(2), UB(3), DR(4), DF(5), DL(6), DB(7), FR(8), FL(9), BL(10), BR(11)
  private val EDGE_FACELETS: Array<IntArray> = arrayOf(
    intArrayOf(5, 10),  // 0: UR (U5, R1)
    intArrayOf(7, 19),  // 1: UF (U7, F1)
    intArrayOf(3, 37),  // 2: UL (U3, L1)
    intArrayOf(1, 46),  // 3: UB (U1, B1)
    intArrayOf(32, 16), // 4: DR (D5, R7)
    intArrayOf(28, 25), // 5: DF (D1, F7)
    intArrayOf(30, 43), // 6: DL (D3, L7)
    intArrayOf(34, 52), // 7: DB (D7, B7)
    intArrayOf(23, 12), // 8: FR (F5, R3)
    intArrayOf(21, 41), // 9: FL (F3, L5)
    intArrayOf(50, 39), // 10: BL (B5, L3)
    intArrayOf(48, 14), // 11: BR (B3, R5)
  )

  private val EDGE_COLORS: Array<Array<CubeColor>> = arrayOf(
    arrayOf(CubeColor.WHITE, CubeColor.RED),     // 0: UR
    arrayOf(CubeColor.WHITE, CubeColor.GREEN),   // 1: UF
    arrayOf(CubeColor.WHITE, CubeColor.ORANGE),  // 2: UL
    arrayOf(CubeColor.WHITE, CubeColor.BLUE),    // 3: UB
    arrayOf(CubeColor.YELLOW, CubeColor.RED),    // 4: DR
    arrayOf(CubeColor.YELLOW, CubeColor.GREEN),  // 5: DF
    arrayOf(CubeColor.YELLOW, CubeColor.ORANGE), // 6: DL
    arrayOf(CubeColor.YELLOW, CubeColor.BLUE),   // 7: DB
    arrayOf(CubeColor.GREEN, CubeColor.RED),     // 8: FR
    arrayOf(CubeColor.GREEN, CubeColor.ORANGE),  // 9: FL
    arrayOf(CubeColor.BLUE, CubeColor.ORANGE),   // 10: BL
    arrayOf(CubeColor.BLUE, CubeColor.RED),      // 11: BR
  )

  internal class CubieCube(
    val cp: IntArray = IntArray(8) { it },
    val co: IntArray = IntArray(8),
    val ep: IntArray = IntArray(12) { it },
    val eo: IntArray = IntArray(12),
  ) {
    fun copy(): CubieCube = CubieCube(cp.clone(), co.clone(), ep.clone(), eo.clone())

    fun multiplyCorners(b: CubieCube): CubieCube {
      val nextCp = IntArray(8)
      val nextCo = IntArray(8)
      for (i in 0 until 8) {
        val c = b.cp[i]
        nextCp[i] = cp[c]
        nextCo[i] = (co[c] + b.co[i]) % 3
      }
      return CubieCube(nextCp, nextCo, ep.clone(), eo.clone())
    }

    fun multiplyEdges(b: CubieCube): CubieCube {
      val nextEp = IntArray(12)
      val nextEo = IntArray(12)
      for (i in 0 until 12) {
        val e = b.ep[i]
        nextEp[i] = ep[e]
        nextEo[i] = (eo[e] + b.eo[i]) and 1
      }
      return CubieCube(cp.clone(), co.clone(), nextEp, nextEo)
    }

    fun multiply(b: CubieCube): CubieCube {
      val nextCp = IntArray(8)
      val nextCo = IntArray(8)
      for (i in 0 until 8) {
        val c = b.cp[i]
        nextCp[i] = cp[c]
        nextCo[i] = (co[c] + b.co[i]) % 3
      }
      val nextEp = IntArray(12)
      val nextEo = IntArray(12)
      for (i in 0 until 12) {
        val e = b.ep[i]
        nextEp[i] = ep[e]
        nextEo[i] = (eo[e] + b.eo[i]) and 1
      }
      return CubieCube(nextCp, nextCo, nextEp, nextEo)
    }

    // Phase 1 coordinate: Corner Twist (0..2186)
    var twist: Int
      get() {
        var ret = 0
        for (i in 0 until 7) {
          ret = 3 * ret + co[i]
        }
        return ret
      }
      set(value) {
        var v = value
        var parity = 0
        for (i in 6 downTo 0) {
          val ori = v % 3
          co[i] = ori
          parity += ori
          v /= 3
        }
        co[7] = (3 - (parity % 3)) % 3
      }

    // Phase 1 coordinate: Edge Flip (0..2047)
    var flip: Int
      get() {
        var ret = 0
        for (i in 0 until 11) {
          ret = (ret shl 1) or eo[i]
        }
        return ret
      }
      set(value) {
        var v = value
        var parity = 0
        for (i in 10 downTo 0) {
          val ori = v and 1
          eo[i] = ori
          parity = parity xor ori
          v = v shr 1
        }
        eo[11] = parity
      }

    // Phase 1 + 2 coordinate: UD-Slice Sorted (0..11879).
    // slice = sliceSorted / 24 (0..494), slicePerm = sliceSorted % 24 (0..23)
    var sliceSorted: Int
      get() {
        var a = 0
        var x = 0
        val edge4 = IntArray(4)
        for (j in 11 downTo 0) {
          if (ep[j] in 8..11) {
            a += cnk(11 - j, x + 1)
            edge4[3 - x] = ep[j]
            x++
          }
        }
        var b = 0
        for (j in 3 downTo 1) {
          var k = 0
          while (edge4[j] != j + 8) {
            rotateLeft(edge4, 0, j)
            k++
          }
          b = (j + 1) * b + k
        }
        return 24 * a + b
      }
      set(value) {
        val b = value % 24
        var a = value / 24
        for (i in 0 until 12) ep[i] = 0
        val sliceEdge = intArrayOf(8, 9, 10, 11)
        var remB = b
        val coeff = IntArray(4)
        for (j in 1..3) {
          coeff[j] = remB % (j + 1)
          remB /= (j + 1)
        }
        for (j in 1..3) {
          repeat(coeff[j]) { rotateRight(sliceEdge, 0, j) }
        }
        var x = 4
        for (j in 0 until 12) {
          if (a - cnk(11 - j, x) >= 0) {
            ep[j] = sliceEdge[4 - x]
            a -= cnk(11 - j, x)
            x--
          }
        }
      }

    // Phase 2 coordinate: Corner Permutation (0..40319)
    var corners: Int
      get() = getPerm8(cp)
      set(value) = setPerm8(cp, value)

    // Phase 2 coordinate: UD-Edges Permutation (0..40319) for edges 0..7
    var udEdges: Int
      get() = getPerm8(ep)
      set(value) = setPerm8(ep, value)
  }

  private fun getPerm8(arr: IntArray): Int {
    val perm = IntArray(8) { arr[it] }
    var b = 0
    for (j in 7 downTo 1) {
      var k = 0
      while (perm[j] != j) {
        rotateLeft(perm, 0, j)
        k++
      }
      b = (j + 1) * b + k
    }
    return b
  }

  private fun setPerm8(arr: IntArray, idx: Int) {
    for (i in 0 until 8) arr[i] = i
    var rem = idx
    val coeff = IntArray(8)
    for (j in 1..7) {
      coeff[j] = rem % (j + 1)
      rem /= (j + 1)
    }
    for (j in 1..7) {
      repeat(coeff[j]) { rotateRight(arr, 0, j) }
    }
  }

  private fun rotateLeft(arr: IntArray, l: Int, r: Int) {
    val tmp = arr[l]
    for (i in l until r) arr[i] = arr[i + 1]
    arr[r] = tmp
  }

  private fun rotateRight(arr: IntArray, l: Int, r: Int) {
    val tmp = arr[r]
    for (i in r downTo l + 1) arr[i] = arr[i - 1]
    arr[l] = tmp
  }

  private fun cnk(n: Int, k: Int): Int {
    if (n < k || k < 0) return 0
    val kk = if (k > n / 2) n - k else k
    var res = 1
    for (i in 1..kk) {
      res = res * (n - i + 1) / i
    }
    return res
  }

  /**
   * Validates a 54-sticker [CubeState] and returns either [CubeValidationResult.Valid]
   * or a detailed, actionable [CubeValidationResult.Invalid].
   */
  fun validate(rawState: CubeState): CubeValidationResult {
    // 1. Check center uniqueness
    val centerColors = CubeFace.entries.map { rawState.stickers[it.ordinal * 9 + 4] }
    if (centerColors.distinct().size != 6) {
      return CubeValidationResult.Invalid(
        reason = "Duplicate center colors",
        details = "Each of the 6 center stickers must be a distinct color (White, Red, Green, Yellow, Orange, Blue).",
      )
    }

    // 2. Check color counts (9 of each color)
    val counts = CubeColor.entries.associateWith { color -> rawState.stickers.count { it == color } }
    val wrongCounts = counts.filterValues { it != 9 }
    if (wrongCounts.isNotEmpty()) {
      val summary = wrongCounts.entries.joinToString(", ") { "${it.key.displayName}: ${it.value}" }
      return CubeValidationResult.Invalid(
        reason = "Sticker color count mismatch",
        details = "Every color must appear exactly 9 times. Found $summary.",
      )
    }

    val normalized = rawState.normalizedByCenters()
    val cubie = parseCubieCube(normalized)
      ?: return CubeValidationResult.Invalid(
        reason = "Impossible corner or edge piece",
        details = "One or more corner/edge pieces have an invalid color combination (e.g. opposite colors on the same piece). Check your scanned stickers.",
      )

    // 3. Check corner twist parity
    if (cubie.co.sum() % 3 != 0) {
      return CubeValidationResult.Invalid(
        reason = "Twisted corner detected",
        details = "Corner orientations do not sum to a valid multiple of 3. A corner sticker may have been misread.",
      )
    }

    // 4. Check edge flip parity
    if (cubie.eo.sum() % 2 != 0) {
      return CubeValidationResult.Invalid(
        reason = "Flipped edge detected",
        details = "Edge orientations do not sum to an even parity. Check the edge stickers on your cube.",
      )
    }

    // 5. Check total permutation parity
    if (permutationParity(cubie.cp) != permutationParity(cubie.ep)) {
      return CubeValidationResult.Invalid(
        reason = "Swapped pieces (impossible parity)",
        details = "Corner and edge permutation parities do not match. Double-check two swapped stickers.",
      )
    }

    return CubeValidationResult.Valid
  }

  private fun permutationParity(perm: IntArray): Int {
    var inversions = 0
    for (i in perm.indices) {
      for (j in i + 1 until perm.size) {
        if (perm[i] > perm[j]) inversions++
      }
    }
    return inversions and 1
  }

  internal fun parseCubieCube(normalizedState: CubeState): CubieCube? {
    val s = normalizedState.stickers
    val cp = IntArray(8) { -1 }
    val co = IntArray(8)
    val usedCorners = BooleanArray(8)

    for (i in 0 until 8) {
      val f = CORNER_FACELETS[i]
      val c0 = s[f[0]]
      val c1 = s[f[1]]
      val c2 = s[f[2]]
      // Find which home corner has these 3 colors in the same cyclic order
      var matchedCorner = -1
      var matchedOri = 0
      for (j in 0 until 8) {
        val hc = CORNER_COLORS[j]
        for (ori in 0..2) {
          if (c0 == hc[(0 - ori + 3) % 3] &&
            c1 == hc[(1 - ori + 3) % 3] &&
            c2 == hc[(2 - ori + 3) % 3]
          ) {
            matchedCorner = j
            matchedOri = ori
            break
          }
        }
        if (matchedCorner != -1) break
      }
      if (matchedCorner == -1 || usedCorners[matchedCorner]) return null
      usedCorners[matchedCorner] = true
      cp[i] = matchedCorner
      co[i] = matchedOri
    }

    val ep = IntArray(12) { -1 }
    val eo = IntArray(12)
    val usedEdges = BooleanArray(12)

    for (i in 0 until 12) {
      val f = EDGE_FACELETS[i]
      val c0 = s[f[0]]
      val c1 = s[f[1]]
      var matchedEdge = -1
      var matchedOri = 0
      for (j in 0 until 12) {
        val he = EDGE_COLORS[j]
        if (c0 == he[0] && c1 == he[1]) {
          matchedEdge = j
          matchedOri = 0
          break
        } else if (c0 == he[1] && c1 == he[0]) {
          matchedEdge = j
          matchedOri = 1
          break
        }
      }
      if (matchedEdge == -1 || usedEdges[matchedEdge]) return null
      usedEdges[matchedEdge] = true
      ep[i] = matchedEdge
      eo[i] = matchedOri
    }

    return CubieCube(cp, co, ep, eo)
  }

  // All 18 moves ordered by face (U, R, F, D, L, B) and power (90° CW, 180°, 90° CCW):
  // Index = face * 3 + powerIndex (0 -> CW, 1 -> 180, 2 -> CCW)
  private val ALL_MOVES: Array<CubeMove> = arrayOf(
    CubeMove.U, CubeMove.U2, CubeMove.U_PRIME,
    CubeMove.R, CubeMove.R2, CubeMove.R_PRIME,
    CubeMove.F, CubeMove.F2, CubeMove.F_PRIME,
    CubeMove.D, CubeMove.D2, CubeMove.D_PRIME,
    CubeMove.L, CubeMove.L2, CubeMove.L_PRIME,
    CubeMove.B, CubeMove.B2, CubeMove.B_PRIME,
  )

  // Phase 2 allowed move indices in ALL_MOVES (10 moves):
  // U, U2, U', R2, F2, D, D2, D', L2, B2
  private val PHASE2_MOVES: IntArray = intArrayOf(
    0, 1, 2,   // U, U2, U'
    4,         // R2
    7,         // F2
    9, 10, 11, // D, D2, D'
    13,        // L2
    16,        // B2
  )

  private object Tables {
    // 18 move cubes derived directly from CubeState.solved().applyMove(move)
    val moveCubes: Array<CubieCube> = Array(18) { moveIdx ->
      val state = CubeState.solved().applyMove(ALL_MOVES[moveIdx])
      parseCubieCube(state)!!
    }

    val twistMove = Array(2187) { ShortArray(18) }
    val flipMove = Array(2048) { ShortArray(18) }
    val sliceSortedMove = Array(11880) { ShortArray(18) }
    val cornersMove = Array(40320) { IntArray(18) }
    val udEdgesMove = Array(40320) { IntArray(10) }

    val twistSlicePrun = ByteArray(2187 * 495) { -1 }
    val flipSlicePrun = ByteArray(2048 * 495) { -1 }
    val cornersSlicePrun = ByteArray(40320 * 24) { -1 }
    val udEdgesSlicePrun = ByteArray(40320 * 24) { -1 }

    init {
      for (i in 0 until 2187) {
        val c = CubieCube().apply { twist = i }
        for (m in 0 until 18) {
          twistMove[i][m] = c.multiplyCorners(moveCubes[m]).twist.toShort()
        }
      }
      for (i in 0 until 2048) {
        val c = CubieCube().apply { flip = i }
        for (m in 0 until 18) {
          flipMove[i][m] = c.multiplyEdges(moveCubes[m]).flip.toShort()
        }
      }
      for (i in 0 until 11880) {
        val c = CubieCube().apply { sliceSorted = i }
        for (m in 0 until 18) {
          sliceSortedMove[i][m] = c.multiplyEdges(moveCubes[m]).sliceSorted.toShort()
        }
      }
      for (i in 0 until 40320) {
        val c = CubieCube().apply { corners = i }
        for (m in 0 until 18) {
          cornersMove[i][m] = c.multiplyCorners(moveCubes[m]).corners
        }
      }
      for (i in 0 until 40320) {
        val c = CubieCube().apply { udEdges = i }
        for (p2Idx in PHASE2_MOVES.indices) {
          val m = PHASE2_MOVES[p2Idx]
          udEdgesMove[i][p2Idx] = c.multiplyEdges(moveCubes[m]).udEdges
        }
      }

      // Build BFS pruning tables
      buildPhase1Prun(twistSlicePrun, 2187, twistMove)
      buildPhase1Prun(flipSlicePrun, 2048, flipMove)
      buildPhase2CornersPrun()
      buildPhase2UdEdgesPrun()
    }

    private fun buildPhase1Prun(
      table: ByteArray,
      coordSize: Int,
      coordMove: Array<ShortArray>,
    ) {
      val queue = IntArray(coordSize * 495)
      var head = 0
      var tail = 0
      table[0] = 0
      queue[tail++] = 0
      while (head < tail) {
        val curr = queue[head++]
        val d = table[curr].toInt()
        val coord = curr / 495
        val slice = curr % 495
        val sliceSortedBase = slice * 24
        for (m in 0 until 18) {
          val nextCoord = coordMove[coord][m].toInt()
          val nextSlice = sliceSortedMove[sliceSortedBase][m].toInt() / 24
          val nextIdx = nextCoord * 495 + nextSlice
          if (table[nextIdx] == (-1).toByte()) {
            table[nextIdx] = (d + 1).toByte()
            queue[tail++] = nextIdx
          }
        }
      }
    }

    private fun buildPhase2CornersPrun() {
      val queue = IntArray(40320 * 24)
      var head = 0
      var tail = 0
      cornersSlicePrun[0] = 0
      queue[tail++] = 0
      while (head < tail) {
        val curr = queue[head++]
        val d = cornersSlicePrun[curr].toInt()
        val corners = curr / 24
        val slicePerm = curr % 24
        for (p2Idx in PHASE2_MOVES.indices) {
          val m = PHASE2_MOVES[p2Idx]
          val nextCorners = cornersMove[corners][m]
          val nextSlicePerm = sliceSortedMove[slicePerm][m].toInt()
          val nextIdx = nextCorners * 24 + nextSlicePerm
          if (cornersSlicePrun[nextIdx] == (-1).toByte()) {
            cornersSlicePrun[nextIdx] = (d + 1).toByte()
            queue[tail++] = nextIdx
          }
        }
      }
    }

    private fun buildPhase2UdEdgesPrun() {
      val queue = IntArray(40320 * 24)
      var head = 0
      var tail = 0
      udEdgesSlicePrun[0] = 0
      queue[tail++] = 0
      while (head < tail) {
        val curr = queue[head++]
        val d = udEdgesSlicePrun[curr].toInt()
        val udEdges = curr / 24
        val slicePerm = curr % 24
        for (p2Idx in PHASE2_MOVES.indices) {
          val m = PHASE2_MOVES[p2Idx]
          val nextUdEdges = udEdgesMove[udEdges][p2Idx]
          val nextSlicePerm = sliceSortedMove[slicePerm][m].toInt()
          val nextIdx = nextUdEdges * 24 + nextSlicePerm
          if (udEdgesSlicePrun[nextIdx] == (-1).toByte()) {
            udEdgesSlicePrun[nextIdx] = (d + 1).toByte()
            queue[tail++] = nextIdx
          }
        }
      }
    }
  }

  /**
   * Warms up the solver tables in the background so user solves are instantaneous.
   */
  fun warmUp() {
    Tables.twistMove.size
  }

  /**
   * Computes a fast, short solution for [initialState] and builds the complete list
   * of intermediate [CubeState]s so the user can step forward and backward smoothly.
   */
  fun solve(initialState: CubeState, maxTotalDepth: Int = 22): SolveResult {
    val startTime = System.currentTimeMillis()
    val normalized = initialState.normalizedByCenters()
    if (normalized.isSolved) {
      return SolveResult(
        moves = emptyList(),
        statesAtEachStep = listOf(initialState),
        solveTimeMs = 0L,
      )
    }

    val validation = validate(initialState)
    require(validation is CubeValidationResult.Valid) {
      val inv = validation as CubeValidationResult.Invalid
      "${inv.reason}: ${inv.details}"
    }

    val startCubie = parseCubieCube(normalized)!!
    val solverInstance = TwoPhaseSearch(startCubie)
    val moves = solverInstance.findBestSolution(maxTotalDepth)
    val simplifiedMoves = simplifyMoves(moves)

    val states = ArrayList<CubeState>(simplifiedMoves.size + 1)
    var current = initialState
    states.add(current)
    for (move in simplifiedMoves) {
      current = current.applyMove(move)
      states.add(current)
    }

    val elapsed = System.currentTimeMillis() - startTime
    return SolveResult(
      moves = simplifiedMoves,
      statesAtEachStep = states,
      solveTimeMs = elapsed,
    )
  }

  private fun simplifyMoves(moves: List<CubeMove>): List<CubeMove> {
    val stack = ArrayDeque<CubeMove>()
    for (move in moves) {
      val top = stack.peekLast()
      if (top != null && top.face == move.face) {
        val prev = stack.removeLast()
        val combinedQuarterTurns = (prev.clockwiseQuarterTurns + move.clockwiseQuarterTurns) % 4
        val merged = CubeMove.fromFaceAndQuarterTurns(move.face, combinedQuarterTurns)
        if (merged != null) {
          stack.addLast(merged)
        }
      } else {
        stack.addLast(move)
      }
    }
    return stack.toList()
  }

  private class TwoPhaseSearch(private val rootCubie: CubieCube) {
    private val phase1Moves = IntArray(15)
    private val phase2Moves = IntArray(18)
    private var bestSolution: List<CubeMove>? = null
    private var bestLength: Int = Int.MAX_VALUE
    private var solutionsFound: Int = 0

    fun findBestSolution(maxDepth: Int): List<CubeMove> {
      val rootTwist = rootCubie.twist
      val rootFlip = rootCubie.flip
      val rootSliceSorted = rootCubie.sliceSorted

      for (depth1 in 0..12) {
        if (depth1 >= bestLength) break
        searchPhase1(
          twist = rootTwist,
          flip = rootFlip,
          sliceSorted = rootSliceSorted,
          depth = 0,
          maxDepth1 = depth1,
          lastFace = -1,
          maxTotal = minOf(maxDepth, bestLength - 1),
        )
        if (bestSolution != null && (bestLength <= 20 || solutionsFound >= 2)) {
          break
        }
      }
      return bestSolution ?: emptyList()
    }

    private fun isRedundantFaceOrder(prevFace: Int, currFace: Int): Boolean {
      if (prevFace == currFace) return true
      // Opposite faces: U(0)-D(3), R(1)-L(4), F(2)-B(5)
      if (prevFace - currFace == 3) return true
      return false
    }

    private fun searchPhase1(
      twist: Int,
      flip: Int,
      sliceSorted: Int,
      depth: Int,
      maxDepth1: Int,
      lastFace: Int,
      maxTotal: Int,
    ) {
      val slice = sliceSorted / 24
      val h1 = max(
        Tables.twistSlicePrun[twist * 495 + slice].toInt(),
        Tables.flipSlicePrun[flip * 495 + slice].toInt(),
      )
      val remaining = maxDepth1 - depth
      if (h1 > remaining) return

      if (remaining == 0) {
        if (twist == 0 && flip == 0 && slice == 0) {
          initPhase2(depth, lastFace, minOf(maxTotal, bestLength - 1) - depth)
        }
        return
      }

      for (m in 0 until 18) {
        val face = m / 3
        if (lastFace != -1 && isRedundantFaceOrder(lastFace, face)) continue
        phase1Moves[depth] = m
        searchPhase1(
          twist = Tables.twistMove[twist][m].toInt(),
          flip = Tables.flipMove[flip][m].toInt(),
          sliceSorted = Tables.sliceSortedMove[sliceSorted][m].toInt(),
          depth = depth + 1,
          maxDepth1 = maxDepth1,
          lastFace = face,
          maxTotal = maxTotal,
        )
        if (solutionsFound >= 2) return
      }
    }

    private fun initPhase2(depth1: Int, lastFace1: Int, maxDepth2Limit: Int) {
      val maxD2 = minOf(maxDepth2Limit, 18)
      if (maxD2 < 0) return

      var corners = rootCubie.corners
      var sliceSorted = rootCubie.sliceSorted
      var cubie = rootCubie
      for (i in 0 until depth1) {
        val m = phase1Moves[i]
        corners = Tables.cornersMove[corners][m]
        sliceSorted = Tables.sliceSortedMove[sliceSorted][m].toInt()
        cubie = cubie.multiplyEdges(Tables.moveCubes[m])
      }
      val udEdges = cubie.udEdges
      val slicePerm = sliceSorted % 24

      val h2 = max(
        Tables.cornersSlicePrun[corners * 24 + slicePerm].toInt(),
        Tables.udEdgesSlicePrun[udEdges * 24 + slicePerm].toInt(),
      )
      if (h2 > maxD2) return

      for (depth2 in h2..maxD2) {
        if (searchPhase2(corners, udEdges, slicePerm, 0, depth2, lastFace1)) {
          val totalMoves = ArrayList<CubeMove>(depth1 + depth2)
          for (i in 0 until depth1) totalMoves.add(ALL_MOVES[phase1Moves[i]])
          for (i in 0 until depth2) totalMoves.add(ALL_MOVES[phase2Moves[i]])
          bestSolution = totalMoves
          bestLength = totalMoves.size
          solutionsFound++
          return
        }
      }
    }

    private fun searchPhase2(
      corners: Int,
      udEdges: Int,
      slicePerm: Int,
      depth: Int,
      maxDepth2: Int,
      lastFace: Int,
    ): Boolean {
      val h2 = max(
        Tables.cornersSlicePrun[corners * 24 + slicePerm].toInt(),
        Tables.udEdgesSlicePrun[udEdges * 24 + slicePerm].toInt(),
      )
      val remaining = maxDepth2 - depth
      if (h2 > remaining) return false
      if (remaining == 0) {
        return corners == 0 && udEdges == 0 && slicePerm == 0
      }

      for (p2Idx in PHASE2_MOVES.indices) {
        val m = PHASE2_MOVES[p2Idx]
        val face = m / 3
        if (depth == 0) {
          if (lastFace == face) continue
        } else if (lastFace != -1 && isRedundantFaceOrder(lastFace, face)) {
          continue
        }
        phase2Moves[depth] = m
        if (searchPhase2(
            corners = Tables.cornersMove[corners][m],
            udEdges = Tables.udEdgesMove[udEdges][p2Idx],
            slicePerm = Tables.sliceSortedMove[slicePerm][m].toInt(),
            depth = depth + 1,
            maxDepth2 = maxDepth2,
            lastFace = face,
          )
        ) {
          return true
        }
      }
      return false
    }
  }
}
