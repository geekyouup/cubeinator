package com.example.cubeinator.cube

import androidx.compose.ui.graphics.Color

/**
 * Standard 6 faces of a 3x3x3 Rubik's Cube in Singmaster/Kociemba order:
 * U (Up), R (Right), F (Front), D (Down), L (Left), B (Back).
 */
enum class CubeFace(
  val symbol: String,
  val displayName: String,
  val defaultColor: CubeColor,
  val scanInstruction: String,
  val orientationHint: String,
) {
  U(
    symbol = "U",
    displayName = "Top (Up)",
    defaultColor = CubeColor.WHITE,
    scanInstruction = "Show the WHITE center face",
    orientationHint = "Keep BLUE on top, GREEN on bottom",
  ),
  R(
    symbol = "R",
    displayName = "Right",
    defaultColor = CubeColor.RED,
    scanInstruction = "Show the RED center face",
    orientationHint = "Keep WHITE on top, GREEN on left",
  ),
  F(
    symbol = "F",
    displayName = "Front",
    defaultColor = CubeColor.GREEN,
    scanInstruction = "Show the GREEN center face",
    orientationHint = "Keep WHITE on top, RED on right",
  ),
  D(
    symbol = "D",
    displayName = "Bottom (Down)",
    defaultColor = CubeColor.YELLOW,
    scanInstruction = "Show the YELLOW center face",
    orientationHint = "Keep GREEN on top, BLUE on bottom",
  ),
  L(
    symbol = "L",
    displayName = "Left",
    defaultColor = CubeColor.ORANGE,
    scanInstruction = "Show the ORANGE center face",
    orientationHint = "Keep WHITE on top, GREEN on right",
  ),
  B(
    symbol = "B",
    displayName = "Back",
    defaultColor = CubeColor.BLUE,
    scanInstruction = "Show the BLUE center face",
    orientationHint = "Keep WHITE on top, ORANGE on right",
  );

  companion object {
    /**
     * Intuitive scanning order for the user holding the cube:
     * Front -> Right -> Back -> Left (horizontal 360° spin with White on top),
     * then Top (Up) -> Bottom (Down).
     */
    val GUIDED_SCAN_ORDER: List<CubeFace> = listOf(F, R, B, L, U, D)
  }
}

/**
 * The 6 standard Rubik's Cube sticker colors.
 */
enum class CubeColor(
  val displayName: String,
  val shortCode: Char,
  val composeColor: Color,
  val borderColor: Color,
  val refRgb: Triple<Int, Int, Int>,
) {
  WHITE(
    displayName = "White",
    shortCode = 'W',
    composeColor = Color(0xFFF8FAFC),
    borderColor = Color(0xFFCBD5E1),
    refRgb = Triple(240, 244, 248),
  ),
  RED(
    displayName = "Red",
    shortCode = 'R',
    composeColor = Color(0xFFEF233C),
    borderColor = Color(0xFFB91C1C),
    refRgb = Triple(215, 35, 45),
  ),
  GREEN(
    displayName = "Green",
    shortCode = 'G',
    composeColor = Color(0xFF10B981),
    borderColor = Color(0xFF047857),
    refRgb = Triple(20, 175, 95),
  ),
  YELLOW(
    displayName = "Yellow",
    shortCode = 'Y',
    composeColor = Color(0xFFFACC15),
    borderColor = Color(0xFFCA8A04),
    refRgb = Triple(245, 210, 25),
  ),
  ORANGE(
    displayName = "Orange",
    shortCode = 'O',
    composeColor = Color(0xFFFF7A00),
    borderColor = Color(0xFFC2410C),
    refRgb = Triple(255, 125, 15),
  ),
  BLUE(
    displayName = "Blue",
    shortCode = 'B',
    composeColor = Color(0xFF2563EB),
    borderColor = Color(0xFF1D4ED8),
    refRgb = Triple(35, 95, 225),
  );

  val homeFace: CubeFace
    get() = when (this) {
      WHITE -> CubeFace.U
      RED -> CubeFace.R
      GREEN -> CubeFace.F
      YELLOW -> CubeFace.D
      ORANGE -> CubeFace.L
      BLUE -> CubeFace.B
    }
}

/**
 * All 18 standard face rotations in Singmaster notation.
 * [turns]: 1 = 90° clockwise, -1 = 90° counter-clockwise ('), 2 = 180° (2).
 */
enum class CubeMove(
  val notation: String,
  val face: CubeFace,
  val turns: Int,
) {
  U("U", CubeFace.U, 1),
  U2("U2", CubeFace.U, 2),
  U_PRIME("U'", CubeFace.U, -1),

  R("R", CubeFace.R, 1),
  R2("R2", CubeFace.R, 2),
  R_PRIME("R'", CubeFace.R, -1),

  F("F", CubeFace.F, 1),
  F2("F2", CubeFace.F, 2),
  F_PRIME("F'", CubeFace.F, -1),

  D("D", CubeFace.D, 1),
  D2("D2", CubeFace.D, 2),
  D_PRIME("D'", CubeFace.D, -1),

  L("L", CubeFace.L, 1),
  L2("L2", CubeFace.L, 2),
  L_PRIME("L'", CubeFace.L, -1),

  B("B", CubeFace.B, 1),
  B2("B2", CubeFace.B, 2),
  B_PRIME("B'", CubeFace.B, -1);

  /**
   * Returns the exact inverse move for stepping backward through the solution.
   */
  fun inverse(): CubeMove = when (this) {
    U -> U_PRIME
    U_PRIME -> U
    U2 -> U2
    R -> R_PRIME
    R_PRIME -> R
    R2 -> R2
    F -> F_PRIME
    F_PRIME -> F
    F2 -> F2
    D -> D_PRIME
    D_PRIME -> D
    D2 -> D2
    L -> L_PRIME
    L_PRIME -> L
    L2 -> L2
    B -> B_PRIME
    B_PRIME -> B
    B2 -> B2
  }

  /**
   * Number of quarter-turns clockwise (1, 2, or 3).
   */
  val clockwiseQuarterTurns: Int
    get() = when (turns) {
      1 -> 1
      2 -> 2
      -1 -> 3
      else -> 0
    }

  val directionLabel: String
    get() = when (turns) {
      1 -> "90° Clockwise ↻"
      -1 -> "90° Counter-Clockwise ↺"
      2 -> "180° Double Turn ↻↻"
      else -> ""
    }

  val humanInstruction: String
    get() {
      val faceDesc = "${face.displayName} (${face.defaultColor.displayName})"
      return when (turns) {
        1 -> "Rotate $faceDesc face 90° clockwise"
        -1 -> "Rotate $faceDesc face 90° counter-clockwise"
        2 -> "Rotate $faceDesc face 180° (half turn)"
        else -> ""
      }
    }

  companion object {
    fun fromFaceAndQuarterTurns(face: CubeFace, quarterTurnsMod4: Int): CubeMove? {
      return when (((quarterTurnsMod4 % 4) + 4) % 4) {
        1 -> entries.first { it.face == face && it.turns == 1 }
        2 -> entries.first { it.face == face && it.turns == 2 }
        3 -> entries.first { it.face == face && it.turns == -1 }
        else -> null
      }
    }

    fun parseSequence(sequence: String): List<CubeMove> {
      if (sequence.isBlank()) return emptyList()
      return sequence.trim().split(Regex("\\s+")).mapNotNull { token ->
        entries.find { it.notation.equals(token, ignoreCase = true) }
      }
    }
  }
}

/**
 * Immutable 54-sticker state of a 3x3x3 Rubik's Cube.
 * Stickers are indexed 0..53:
 * - 0..8: U (Up)
 * - 9..17: R (Right)
 * - 18..26: F (Front)
 * - 27..35: D (Down)
 * - 36..44: L (Left)
 * - 45..53: B (Back)
 */
data class CubeState(
  val stickers: List<CubeColor>,
) {
  init {
    require(stickers.size == 54) { "CubeState requires 54 stickers, got ${stickers.size}" }
  }

  fun faceStickers(face: CubeFace): List<CubeColor> {
    val start = face.ordinal * 9
    return stickers.subList(start, start + 9)
  }

  fun stickerAt(face: CubeFace, row: Int, col: Int): CubeColor {
    return stickers[face.ordinal * 9 + row * 3 + col]
  }

  fun withSticker(face: CubeFace, indexInFace: Int, color: CubeColor): CubeState {
    val mutable = stickers.toMutableList()
    mutable[face.ordinal * 9 + indexInFace] = color
    return CubeState(mutable)
  }

  fun withFace(face: CubeFace, faceColors: List<CubeColor>): CubeState {
    require(faceColors.size == 9)
    val mutable = stickers.toMutableList()
    val start = face.ordinal * 9
    for (i in 0 until 9) {
      mutable[start + i] = faceColors[i]
    }
    return CubeState(mutable)
  }

  val isSolved: Boolean
    get() = CubeFace.entries.all { face ->
      val center = stickers[face.ordinal * 9 + 4]
      (0 until 9).all { i -> stickers[face.ordinal * 9 + i] == center }
    }

  /**
   * Applies a single [CubeMove] to this [CubeState] and returns the resulting [CubeState].
   */
  fun applyMove(move: CubeMove): CubeState {
    var result = this
    repeat(move.clockwiseQuarterTurns) {
      result = result.applyClockwiseQuarterTurn(move.face)
    }
    return result
  }

  /**
   * Applies a sequence of moves in order.
   */
  fun applyMoves(moves: Iterable<CubeMove>): CubeState {
    return moves.fold(this) { acc, move -> acc.applyMove(move) }
  }

  private fun applyClockwiseQuarterTurn(face: CubeFace): CubeState {
    val s = stickers.toMutableList()
    val base = face.ordinal * 9
    // Rotate the 3x3 face itself 90° clockwise:
    // 0 1 2      6 3 0
    // 3 4 5  ->  7 4 1
    // 6 7 8      8 5 2
    s[base + 0] = stickers[base + 6]
    s[base + 1] = stickers[base + 3]
    s[base + 2] = stickers[base + 0]
    s[base + 3] = stickers[base + 7]
    s[base + 4] = stickers[base + 4]
    s[base + 5] = stickers[base + 1]
    s[base + 6] = stickers[base + 8]
    s[base + 7] = stickers[base + 5]
    s[base + 8] = stickers[base + 2]

    // Cycle the 12 adjacent stickers clockwise when looking directly at `face`:
    val ring = ADJACENT_RINGS.getValue(face)
    // ring has 4 groups of 3 indices: [top, right, bottom, left]
    // Clockwise turn moves: left -> top -> right -> bottom -> left
    for (i in 0 until 3) {
      s[ring[0][i]] = stickers[ring[3][i]]
      s[ring[1][i]] = stickers[ring[0][i]]
      s[ring[2][i]] = stickers[ring[1][i]]
      s[ring[3][i]] = stickers[ring[2][i]]
    }
    return CubeState(s)
  }

  /**
   * Normalizes center orientation so that whichever color is at center U/R/F/D/L/B
   * maps to the canonical U/R/F/D/L/B color for the solver.
   */
  fun normalizedByCenters(): CubeState {
    val centerColors = CubeFace.entries.map { stickers[it.ordinal * 9 + 4] }
    if (centerColors.distinct().size != 6) return this
    val colorMap = centerColors.withIndex().associate { (faceIdx, color) ->
      color to CubeFace.entries[faceIdx].defaultColor
    }
    return CubeState(stickers.map { colorMap.getValue(it) })
  }

  companion object {
    /**
     * Solved cube state with canonical colors on all 6 faces.
     */
    fun solved(): CubeState {
      val list = ArrayList<CubeColor>(54)
      for (face in CubeFace.entries) {
        repeat(9) { list.add(face.defaultColor) }
      }
      return CubeState(list)
    }

    /**
     * Exact adjacent 3-sticker strips around each face in clockwise order [top, right, bottom, left]
     * so that `ring[next][i]` receives `ring[prev][i]` for i = 0, 1, 2.
     */
    private val ADJACENT_RINGS: Map<CubeFace, Array<IntArray>> = mapOf(
      // Looking at U (top=B, right=R, bottom=F, left=L):
      // Clockwise U moves B(top row) -> R(top row) -> F(top row) -> L(top row) -> B(top row).
      CubeFace.U to arrayOf(
        intArrayOf(47, 46, 45), // B2, B1, B0
        intArrayOf(11, 10, 9),  // R2, R1, R0
        intArrayOf(20, 19, 18), // F2, F1, F0
        intArrayOf(38, 37, 36), // L2, L1, L0
      ),
      // Looking at R (top=U, right=B, bottom=D, left=F):
      // Clockwise R moves F(right col) -> U(right col) -> B(left col) -> D(right col) -> F(right col).
      CubeFace.R to arrayOf(
        intArrayOf(8, 5, 2),    // U8, U5, U2
        intArrayOf(45, 48, 51), // B0, B3, B6
        intArrayOf(35, 32, 29), // D8, D5, D2
        intArrayOf(26, 23, 20), // F8, F5, F2
      ),
      // Looking at F (top=U, right=R, bottom=D, left=L):
      // Clockwise F moves L(right col) -> U(bottom row) -> R(left col) -> D(top row) -> L(right col).
      CubeFace.F to arrayOf(
        intArrayOf(6, 7, 8),    // U6, U7, U8
        intArrayOf(9, 12, 15),  // R0, R3, R6
        intArrayOf(29, 28, 27), // D2, D1, D0
        intArrayOf(44, 41, 38), // L8, L5, L2
      ),
      // Looking at D (top=F, right=R, bottom=B, left=L):
      // Clockwise D moves L(bottom row) -> F(bottom row) -> R(bottom row) -> B(bottom row) -> L(bottom row).
      CubeFace.D to arrayOf(
        intArrayOf(24, 25, 26), // F6, F7, F8
        intArrayOf(15, 16, 17), // R6, R7, R8
        intArrayOf(51, 52, 53), // B6, B7, B8
        intArrayOf(42, 43, 44), // L6, L7, L8
      ),
      // Looking at L (top=U, right=F, bottom=D, left=B):
      // Clockwise L moves B(right col) -> U(left col) -> F(left col) -> D(left col) -> B(right col).
      CubeFace.L to arrayOf(
        intArrayOf(0, 3, 6),    // U0, U3, U6
        intArrayOf(18, 21, 24), // F0, F3, F6
        intArrayOf(27, 30, 33), // D0, D3, D6
        intArrayOf(53, 50, 47), // B8, B5, B2
      ),
      // Looking at B (top=U, right=L, bottom=D, left=R):
      // Clockwise B moves R(right col) -> U(top row) -> L(left col) -> D(bottom row) -> R(right col).
      CubeFace.B to arrayOf(
        intArrayOf(2, 1, 0),    // U2, U1, U0
        intArrayOf(36, 39, 42), // L0, L3, L6
        intArrayOf(33, 34, 35), // D6, D7, D8
        intArrayOf(17, 14, 11), // R8, R5, R2
      ),
    )
  }
}

/**
 * Sample scrambles for instant testing, demos, and learning.
 */
data class SampleScramble(
  val title: String,
  val subtitle: String,
  val movesNotation: String,
) {
  val moves: List<CubeMove>
    get() = CubeMove.parseSequence(movesNotation)

  fun buildState(): CubeState = CubeState.solved().applyMoves(moves)

  companion object {
    val PRESETS: List<SampleScramble> = listOf(
      SampleScramble(
        title = "Quick 6-Move Scramble",
        subtitle = "Great for testing step-by-step forward & reverse animations",
        movesNotation = "R U R' U' F' U",
      ),
      SampleScramble(
        title = "12-Move Classic Scramble",
        subtitle = "Medium scramble solved optimally in seconds",
        movesNotation = "F R U' R' U' R U R' F' R U R' U' R' F R F'",
      ),
      SampleScramble(
        title = "Checkerboard Pattern",
        subtitle = "Iconic symmetric pattern (6 double-turns to solve)",
        movesNotation = "U2 D2 R2 L2 F2 B2",
      ),
      SampleScramble(
        title = "Superflip (God's Number 20)",
        subtitle = "All 12 edges flipped in place — requires 20 moves!",
        movesNotation = "U R2 F B R B2 R U2 L B2 R U' D' R2 F R' L B2 U2 F2",
      ),
    )
  }
}
