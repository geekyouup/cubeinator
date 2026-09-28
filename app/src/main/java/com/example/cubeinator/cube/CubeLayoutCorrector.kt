package com.example.cubeinator.cube

import kotlin.math.abs

/**
 * Result of applying Rubik's Cube physical layout & group-theoretic invariants
 * to automatically correct upside-down/rotated faces, Red <-> Orange misclassifications,
 * and single-sticker misreads during scanning.
 */
data class AutocorrectOutcome(
  val cubeState: CubeState,
  val alignedRawState: CubeState = cubeState,
  val alignedWarmScores: FloatArray? = null,
  val redOrangeFixesCount: Int,
  val rotatedFacesCount: Int = 0,
  val summaryMessage: String? = null,
)

/**
 * Uses exact 3x3x3 Rubik's Cube physical layout laws to validate the cube as it is scanning
 * and automatically fix upside-down/rotated faces (90°/180°/270°), Red <-> Orange camera
 * misclassifications, and single-sticker misreads:
 *
 * 1. **Fixed Center Invariant**:
 *    U=White, R=Red, F=Green, D=Yellow, L=Orange, B=Blue.
 *
 * 2. **Edge & Corner Axis Invariants (During Partial & Full Scanning)**:
 *    - Opposite colors (White/Yellow, Green/Blue, Red/Orange) and identical colors share the same
 *      3D axis and can NEVER appear together on the same edge or the same corner.
 *    - Every physical edge connects stickers from 2 distinct axes.
 *    - Every physical corner connects stickers from all 3 distinct axes ({UD, FB, RL}).
 *    - No non-warm edge or 3-face corner cubie can appear twice on the cube.
 *    If a face is scanned upside-down (180°) or sideways (90°/270°), its shared edges and corners
 *    immediately violate these invariants, allowing us to detect and rotate the face into its
 *    valid orientation while scanning!
 *
 * 3. **3D Corner Chirality Theorem (100% Deterministic for all 8 Corners)**:
 *    Every physical corner contains one {WHITE, YELLOW} sticker, one {GREEN, BLUE} sticker,
 *    and one {RED, ORANGE} sticker. Because 3D corner cubies cannot be mirrored, the clockwise
 *    order of the {WHITE, YELLOW} and {GREEN, BLUE} stickers around any corner vertex 100%
 *    uniquely determines whether the third sticker on that corner is RED or ORANGE!
 *
 * 4. **Warm Edge Partner Pairing Law (4 Exact Pairs)**:
 *    For each non-warm partner color P in {WHITE, YELLOW, GREEN, BLUE}, the cube has
 *    exactly ONE (P, RED) edge and ONE (P, ORANGE) edge.
 *
 * 5. **Global Permutation Parity Lock (Kociemba Group Invariant)**:
 *    Corner permutation parity must equal edge permutation parity (`parity(cp) == parity(ep)`).
 */
object CubeLayoutCorrector {

  private val PARTNER_COLORS = listOf(
    CubeColor.WHITE,
    CubeColor.YELLOW,
    CubeColor.GREEN,
    CubeColor.BLUE,
  )

  fun isWarm(color: CubeColor): Boolean =
    color == CubeColor.RED || color == CubeColor.ORANGE

  private fun isUpDown(color: CubeColor): Boolean =
    color == CubeColor.WHITE || color == CubeColor.YELLOW

  private fun isFrontBack(color: CubeColor): Boolean =
    color == CubeColor.GREEN || color == CubeColor.BLUE

  /**
   * Returns the orthogonal 3D axis of a color:
   * - 0: Up/Down ({WHITE, YELLOW})
   * - 1: Front/Back ({GREEN, BLUE})
   * - 2: Right/Left ({RED, ORANGE})
   *
   * Note that this axis is 100% invariant to Red <-> Orange lighting confusion!
   */
  private fun colorAxis(color: CubeColor): Int = when (color) {
    CubeColor.WHITE, CubeColor.YELLOW -> 0
    CubeColor.GREEN, CubeColor.BLUE -> 1
    CubeColor.RED, CubeColor.ORANGE -> 2
  }

  private fun faceOf(stickerIndex: Int): CubeFace =
    CubeFace.entries[stickerIndex / 9]

  /**
   * Rotates a 9-sticker face 90° clockwise [quarterTurns] times in place.
   */
  fun rotateFaceStickersClockwise(
    stickers: List<CubeColor>,
    quarterTurns: Int,
  ): List<CubeColor> {
    var cur = stickers
    val turns = ((quarterTurns % 4) + 4) % 4
    repeat(turns) {
      cur = listOf(
        cur[6], cur[3], cur[0],
        cur[7], cur[4], cur[1],
        cur[8], cur[5], cur[2],
      )
    }
    return cur
  }

  private fun rotateFaceScoresClockwise(
    scores: FloatArray,
    face: CubeFace,
    quarterTurns: Int,
  ): FloatArray {
    val result = scores.clone()
    val turns = ((quarterTurns % 4) + 4) % 4
    val base = face.ordinal * 9
    repeat(turns) {
      val prev = result.sliceArray(base until base + 9)
      result[base + 0] = prev[6]
      result[base + 1] = prev[3]
      result[base + 2] = prev[0]
      result[base + 3] = prev[7]
      result[base + 4] = prev[4]
      result[base + 5] = prev[1]
      result[base + 6] = prev[8]
      result[base + 7] = prev[5]
      result[base + 8] = prev[2]
    }
    return result
  }

  /**
   * Evaluates whether a live candidate face [candidateColors] for [candidateFace] is physically
   * consistent (under some 0°/90°/180°/270° rotation) with the faces already in [capturedFaces].
   * Returns 0 if there is a rotation with zero impossible edge/corner clashes.
   */
  fun bestRotationPenaltyForCandidateFace(
    currentRawState: CubeState,
    capturedFaces: Set<CubeFace>,
    candidateFace: CubeFace,
    candidateColors: List<CubeColor>,
  ): Int {
    val testFaces = capturedFaces + candidateFace
    if (testFaces.size < 2) return 0
    val base = enforceCanonicalCenters(currentRawState)
    var minPenalty = Int.MAX_VALUE
    for (rot in 0..3) {
      val rotatedCandidate = rotateFaceStickersClockwise(candidateColors, rot)
      val stateWithCandidate = base.withFace(candidateFace, rotatedCandidate)
      val penalty = computePartialValidityPenalty(stateWithCandidate.stickers, testFaces)
      if (penalty < minPenalty) {
        minPenalty = penalty
        if (minPenalty == 0) return 0
      }
    }
    // Also check if rotating the first captured face (when only 1 face was previously captured) resolves it
    if (capturedFaces.size == 1) {
      val firstFace = capturedFaces.first()
      for (firstRot in 1..3) {
        val baseRotatedFirst = base.withFace(
          firstFace,
          rotateFaceStickersClockwise(base.faceStickers(firstFace), firstRot),
        )
        for (rot in 0..3) {
          val rotatedCandidate = rotateFaceStickersClockwise(candidateColors, rot)
          val stateWithCandidate = baseRotatedFirst.withFace(candidateFace, rotatedCandidate)
          val penalty = computePartialValidityPenalty(stateWithCandidate.stickers, testFaces)
          if (penalty < minPenalty) {
            minPenalty = penalty
            if (minPenalty == 0) return 0
          }
        }
      }
    }
    return minPenalty
  }

  /**
   * Autocorrects [rawState] using all known Rubik's Cube layout constraints across [capturedFaces].
   * Works both incrementally (as 2..5 faces are scanned) and globally (when all 6 faces are scanned).
   */
  fun autocorrect(
    rawState: CubeState,
    capturedFaces: Set<CubeFace>,
    warmOrangenessScores: FloatArray? = null,
    mostRecentFace: CubeFace? = null,
  ): AutocorrectOutcome {
    if (capturedFaces.size < 2) {
      val centered = enforceCanonicalCenters(rawState)
      return AutocorrectOutcome(
        cubeState = centered,
        alignedRawState = centered,
        alignedWarmScores = warmOrangenessScores,
        redOrangeFixesCount = 0,
      )
    }

    val baseCentered = enforceCanonicalCenters(rawState)

    // Partial scan (2..5 faces):
    // 1. Check if any scanned face (especially the most recently scanned face) is upside-down or rotated (90°/180°/270°)
    // 2. Apply 3D Corner Chirality + Scanned Warm Edge Partner laws incrementally
    if (capturedFaces.size < 6) {
      val (alignedState, alignedScores, rotatedFacesDesc) = alignPartialScannedFaces(
        baseState = baseCentered,
        capturedFaces = capturedFaces,
        warmScores = warmOrangenessScores,
        mostRecentFace = mostRecentFace,
      )

      val s = alignedState.stickers.toMutableList()
      fixScannedCornersByChirality(s, capturedFaces)
      fixScannedWarmEdgePairs(s, capturedFaces, alignedScores)
      val correctedPartial = CubeState(s)
      val warmFixes = countWarmDifferences(alignedState, correctedPartial)

      val parts = ArrayList<String>(2)
      if (rotatedFacesDesc.isNotEmpty()) {
        parts.add("rotated ${rotatedFacesDesc.joinToString(", ")}")
      }
      if (warmFixes > 0) {
        parts.add("fixed $warmFixes Red/Orange sticker${if (warmFixes == 1) "" else "s"}")
      }

      return AutocorrectOutcome(
        cubeState = correctedPartial,
        alignedRawState = alignedState,
        alignedWarmScores = alignedScores,
        redOrangeFixesCount = warmFixes,
        rotatedFacesCount = rotatedFacesDesc.size,
        summaryMessage = if (parts.isNotEmpty()) "Auto-corrected: ${parts.joinToString(" & ")}" else null,
      )
    }

    // Full 6-face scan: first try with 0 face rotations
    val directAttempt = attemptFullCubeAutocorrect(baseCentered, warmOrangenessScores)
    if (CubeSolver.validate(directAttempt.first) is CubeValidationResult.Valid) {
      val fixes = countWarmDifferences(baseCentered, directAttempt.first)
      val msg = if (fixes > 0) {
        "Auto-corrected $fixes sticker${if (fixes == 1) "" else "s"} via cube geometry"
      } else {
        null
      }
      return AutocorrectOutcome(
        cubeState = directAttempt.first,
        alignedRawState = baseCentered,
        alignedWarmScores = warmOrangenessScores,
        redOrangeFixesCount = fixes,
        rotatedFacesCount = 0,
        summaryMessage = msg,
      )
    }

    // Search all 4^6 = 4,096 possible face rotations (0°, 90°, 180°, 270° for each of the 6 faces)
    // using fast edge/corner axis & uniqueness pruning to find any upside-down or sideways faces!
    val globalRotationResult = searchAllSixFaceRotations(baseCentered, warmOrangenessScores)
    if (globalRotationResult != null) {
      return globalRotationResult
    }

    // Return best-effort corner+edge corrected state even if a manual touch-up is still needed
    val fallbackFixes = countWarmDifferences(baseCentered, directAttempt.first)
    return AutocorrectOutcome(
      cubeState = directAttempt.first,
      alignedRawState = baseCentered,
      alignedWarmScores = warmOrangenessScores,
      redOrangeFixesCount = fallbackFixes,
      summaryMessage = if (fallbackFixes > 0) {
        "Auto-corrected $fallbackFixes Red/Orange sticker${if (fallbackFixes == 1) "" else "s"}"
      } else {
        null
      },
    )
  }

  private data class PartialAlignmentResult(
    val alignedState: CubeState,
    val alignedScores: FloatArray?,
    val rotatedFacesDescriptions: List<String>,
  )

  /**
   * Checks whether any of the currently scanned [capturedFaces] (2..5 faces) was scanned
   * upside-down (180°) or sideways (90°/270°), and rotates it if doing so strictly reduces
   * physical edge/corner conflicts between the scanned faces.
   */
  private fun alignPartialScannedFaces(
    baseState: CubeState,
    capturedFaces: Set<CubeFace>,
    warmScores: FloatArray?,
    mostRecentFace: CubeFace?,
  ): PartialAlignmentResult {
    val zeroRotPenalty = computePartialValidityPenalty(baseState.stickers, capturedFaces)
    if (zeroRotPenalty == 0) {
      return PartialAlignmentResult(baseState, warmScores, emptyList())
    }

    val faceList = capturedFaces.toList()
    val n = faceList.size
    val precomputedRotations = Array(n) { idx ->
      val f = faceList[idx]
      val orig = baseState.faceStickers(f)
      Array(4) { rot -> rotateFaceStickersClockwise(orig, rot) }
    }

    var bestValidityPenalty = zeroRotPenalty
    var bestRotationTieCost = 0
    var bestRots = IntArray(n)

    val workingStickers = baseState.stickers.toMutableList()
    val currentRots = IntArray(n)

    fun search(depth: Int, tieCost: Int) {
      if (depth == n) {
        val validityPenalty = computePartialValidityPenalty(workingStickers, capturedFaces)
        if (validityPenalty < bestValidityPenalty ||
          (validityPenalty == bestValidityPenalty && tieCost < bestRotationTieCost)
        ) {
          bestValidityPenalty = validityPenalty
          bestRotationTieCost = tieCost
          bestRots = currentRots.clone()
        }
        return
      }

      val face = faceList[depth]
      val baseOffset = face.ordinal * 9
      for (rot in 0..3) {
        currentRots[depth] = rot
        val rotatedFace = precomputedRotations[depth][rot]
        for (i in 0 until 9) {
          workingStickers[baseOffset + i] = rotatedFace[i]
        }
        val stepCost = if (rot == 0) {
          0
        } else if (face == mostRecentFace) {
          // Rotating the just-scanned face is preferred over rotating previously-settled faces,
          // and 180° (upside down) is the most common human camera rotation
          if (rot == 2) 2 else 3
        } else {
          if (rot == 2) 5 else 6
        }
        search(depth + 1, tieCost + stepCost)
      }
    }

    search(0, 0)

    if (bestValidityPenalty < zeroRotPenalty) {
      var updatedState = baseState
      var updatedScores = warmScores
      val descriptions = ArrayList<String>()
      for (idx in 0 until n) {
        val rot = bestRots[idx]
        if (rot != 0) {
          val face = faceList[idx]
          updatedState = updatedState.withFace(face, precomputedRotations[idx][rot])
          if (updatedScores != null) {
            updatedScores = rotateFaceScoresClockwise(updatedScores, face, rot)
          }
          val deg = rot * 90
          descriptions.add("${face.symbol} (${deg}°)")
        }
      }
      return PartialAlignmentResult(updatedState, updatedScores, descriptions)
    }

    return PartialAlignmentResult(baseState, warmScores, emptyList())
  }

  /**
   * Searches all $4^6 = 4,096$ face rotation combinations across all 6 faces using fast
   * physical edge/corner axis pruning, then runs full Kociemba group-theory autocorrection on
   * the top candidates.
   */
  private fun searchAllSixFaceRotations(
    baseCentered: CubeState,
    warmOrangenessScores: FloatArray?,
  ): AutocorrectOutcome? {
    val allFaces = CubeFace.entries
    val allFacesSet = allFaces.toSet()
    val precomputed = Array(6) { fIdx ->
      val orig = baseCentered.faceStickers(allFaces[fIdx])
      Array(4) { rot -> rotateFaceStickersClockwise(orig, rot) }
    }

    data class RotationCandidate(
      val rots: IntArray,
      val penalty: Int,
      val rotatedCount: Int,
      val tieCost: Int,
    )

    val candidates = ArrayList<RotationCandidate>(64)
    val working = MutableList(54) { CubeColor.WHITE }

    for (r0 in 0..3) {
      val f0 = precomputed[0][r0]
      for (i in 0..8) working[i] = f0[i]
      for (r1 in 0..3) {
        val f1 = precomputed[1][r1]
        for (i in 0..8) working[9 + i] = f1[i]
        for (r2 in 0..3) {
          val f2 = precomputed[2][r2]
          for (i in 0..8) working[18 + i] = f2[i]
          for (r3 in 0..3) {
            val f3 = precomputed[3][r3]
            for (i in 0..8) working[27 + i] = f3[i]
            for (r4 in 0..3) {
              val f4 = precomputed[4][r4]
              for (i in 0..8) working[36 + i] = f4[i]
              for (r5 in 0..3) {
                if (r0 == 0 && r1 == 0 && r2 == 0 && r3 == 0 && r4 == 0 && r5 == 0) continue
                val f5 = precomputed[5][r5]
                for (i in 0..8) working[45 + i] = f5[i]

                val penalty = computePartialValidityPenalty(working, allFacesSet)
                // Keep candidates with 0 physical clashes or at most 1 single-sticker clash (penalty <= 180)
                if (penalty <= 180) {
                  val rots = intArrayOf(r0, r1, r2, r3, r4, r5)
                  var rotCount = 0
                  var tieCost = 0
                  for (r in rots) {
                    if (r != 0) {
                      rotCount++
                      tieCost += if (r == 2) 2 else 3
                    }
                  }
                  candidates.add(RotationCandidate(rots, penalty, rotCount, tieCost))
                }
              }
            }
          }
        }
      }
    }

    candidates.sortWith(
      compareBy<RotationCandidate> { it.penalty }
        .thenBy { it.rotatedCount }
        .thenBy { it.tieCost }
    )

    val maxToEvaluate = minOf(candidates.size, 32)
    for (idx in 0 until maxToEvaluate) {
      val cand = candidates[idx]
      var rotatedState = baseCentered
      var rotatedScores = warmOrangenessScores
      val rotatedDesc = ArrayList<String>()

      for (fIdx in 0 until 6) {
        val r = cand.rots[fIdx]
        if (r != 0) {
          val face = allFaces[fIdx]
          rotatedState = rotatedState.withFace(face, precomputed[fIdx][r])
          if (rotatedScores != null) {
            rotatedScores = rotateFaceScoresClockwise(rotatedScores, face, r)
          }
          rotatedDesc.add("${face.symbol} (${r * 90}°)")
        }
      }

      val (corrected, _) = attemptFullCubeAutocorrect(rotatedState, rotatedScores)
      if (CubeSolver.validate(corrected) is CubeValidationResult.Valid) {
        val warmFixes = countWarmDifferences(rotatedState, corrected)
        val parts = ArrayList<String>(2)
        if (rotatedDesc.isNotEmpty()) {
          parts.add("rotated ${rotatedDesc.joinToString(", ")}")
        }
        if (warmFixes > 0) {
          parts.add("fixed $warmFixes sticker${if (warmFixes == 1) "" else "s"}")
        }
        return AutocorrectOutcome(
          cubeState = corrected,
          alignedRawState = rotatedState,
          alignedWarmScores = rotatedScores,
          redOrangeFixesCount = warmFixes,
          rotatedFacesCount = rotatedDesc.size,
          summaryMessage = if (parts.isNotEmpty()) "Auto-corrected: ${parts.joinToString(" & ")}" else null,
        )
      }
    }

    return null
  }

  /**
   * Computes a fast physical validity penalty across all edges and corners whose faces belong to
   * [capturedFaces]. Returns `0` when all scanned edges and corners satisfy Rubik's Cube
   * axis and uniqueness invariants (independent of Red <-> Orange lighting confusion).
   */
  fun computePartialValidityPenalty(
    stickers: List<CubeColor>,
    capturedFaces: Set<CubeFace>,
  ): Int {
    var penalty = 0

    // 1. Check all scanned edges (both faces in capturedFaces)
    // Non-warm edge counts (4 possible pairs: W-G, W-B, Y-G, Y-B)
    val nonWarmEdgeCounts = IntArray(4)
    // Warm partner edge counts (4 partner colors: W, Y, G, B -> each can appear at most twice)
    val warmPartnerEdgeCounts = IntArray(4)

    for (e in 0 until 12) {
      val f = CubeSolver.EDGE_FACELETS[e]
      if (faceOf(f[0]) !in capturedFaces || faceOf(f[1]) !in capturedFaces) continue
      val c0 = stickers[f[0]]
      val c1 = stickers[f[1]]
      val a0 = colorAxis(c0)
      val a1 = colorAxis(c1)

      if (a0 == a1) {
        // Physically impossible edge: same color or opposite colors (e.g. W-Y, G-B, R-O)
        penalty += 100
      } else if (a0 != 2 && a1 != 2) {
        val udColor = if (a0 == 0) c0 else c1
        val fbColor = if (a0 == 1) c0 else c1
        val pairIdx = (if (udColor == CubeColor.WHITE) 0 else 2) + (if (fbColor == CubeColor.GREEN) 0 else 1)
        nonWarmEdgeCounts[pairIdx]++
        if (nonWarmEdgeCounts[pairIdx] > 1) {
          penalty += 80
        }
      } else {
        val partnerColor = if (a0 != 2) c0 else c1
        val pIdx = PARTNER_COLORS.indexOf(partnerColor)
        if (pIdx >= 0) {
          warmPartnerEdgeCounts[pIdx]++
          if (warmPartnerEdgeCounts[pIdx] > 2) {
            penalty += 80
          }
        }
      }
    }

    // 2. Check all 2-face and 3-face scanned corners
    val cornerSignatureCounts = IntArray(8)
    for (c in 0 until 8) {
      val f = CubeSolver.CORNER_FACELETS[c]
      val in0 = faceOf(f[0]) in capturedFaces
      val in1 = faceOf(f[1]) in capturedFaces
      val in2 = faceOf(f[2]) in capturedFaces
      val scannedCount = (if (in0) 1 else 0) + (if (in1) 1 else 0) + (if (in2) 1 else 0)

      if (scannedCount == 2) {
        val cA = if (!in0) stickers[f[1]] else stickers[f[0]]
        val cB = if (!in2) stickers[f[1]] else stickers[f[2]]
        if (colorAxis(cA) == colorAxis(cB)) {
          // Two stickers on the same corner have the same or opposite colors!
          penalty += 80
        }
      } else if (scannedCount == 3) {
        val c0 = stickers[f[0]]
        val c1 = stickers[f[1]]
        val c2 = stickers[f[2]]
        val a0 = colorAxis(c0)
        val a1 = colorAxis(c1)
        val a2 = colorAxis(c2)
        if (a0 == a1 || a1 == a2 || a0 == a2) {
          // Physically impossible corner: missing one of {UD, FB, RL} axes
          penalty += 100
        } else {
          // Identify the unique physical corner by (UD color, FB color, cyclic chirality)
          val udPos = if (a0 == 0) 0 else if (a1 == 0) 1 else 2
          val fbPos = if (a0 == 1) 0 else if (a1 == 1) 1 else 2
          val udColor = stickers[f[udPos]]
          val fbColor = stickers[f[fbPos]]
          val chiralityBit = if ((fbPos - udPos + 3) % 3 == 1) 0 else 1
          val sig = (if (udColor == CubeColor.WHITE) 0 else 4) +
            (if (fbColor == CubeColor.GREEN) 0 else 2) +
            chiralityBit
          cornerSignatureCounts[sig]++
          if (cornerSignatureCounts[sig] > 1) {
            // Duplicate physical corner cubie!
            penalty += 90
          }
        }
      }
    }

    return penalty
  }

  private fun enforceCanonicalCenters(state: CubeState): CubeState {
    val s = state.stickers.toMutableList()
    for (face in CubeFace.entries) {
      s[face.ordinal * 9 + 4] = face.defaultColor
    }
    return CubeState(s)
  }

  /**
   * Fixes Red <-> Orange on any corner where all 3 faces belong to [capturedFaces],
   * using the 3D clockwise chirality of the corner's {WHITE, YELLOW} and {GREEN, BLUE} stickers.
   */
  private fun fixScannedCornersByChirality(
    stickers: MutableList<CubeColor>,
    capturedFaces: Set<CubeFace>,
  ): Int {
    var fixes = 0
    for (c in 0 until 8) {
      val f = CubeSolver.CORNER_FACELETS[c]
      if (faceOf(f[0]) !in capturedFaces ||
        faceOf(f[1]) !in capturedFaces ||
        faceOf(f[2]) !in capturedFaces
      ) {
        continue
      }
      val colors = arrayOf(stickers[f[0]], stickers[f[1]], stickers[f[2]])
      val warmIndices = (0..2).filter { isWarm(colors[it]) }
      val udIndices = (0..2).filter { isUpDown(colors[it]) }
      val fbIndices = (0..2).filter { isFrontBack(colors[it]) }

      if (warmIndices.size == 1 && udIndices.size == 1 && fbIndices.size == 1) {
        val wPos = warmIndices[0]
        val redValid = isValidCornerTriple(
          c0 = if (wPos == 0) CubeColor.RED else colors[0],
          c1 = if (wPos == 1) CubeColor.RED else colors[1],
          c2 = if (wPos == 2) CubeColor.RED else colors[2],
        )
        val orangeValid = isValidCornerTriple(
          c0 = if (wPos == 0) CubeColor.ORANGE else colors[0],
          c1 = if (wPos == 1) CubeColor.ORANGE else colors[1],
          c2 = if (wPos == 2) CubeColor.ORANGE else colors[2],
        )
        val requiredColor = when {
          redValid && !orangeValid -> CubeColor.RED
          orangeValid && !redValid -> CubeColor.ORANGE
          else -> null
        }
        if (requiredColor != null && stickers[f[wPos]] != requiredColor) {
          stickers[f[wPos]] = requiredColor
          fixes++
        }
      }
    }
    return fixes
  }

  private fun isValidCornerTriple(c0: CubeColor, c1: CubeColor, c2: CubeColor): Boolean {
    for (j in 0 until 8) {
      val hc = CubeSolver.CORNER_COLORS[j]
      for (ori in 0..2) {
        if (c0 == hc[(0 - ori + 3) % 3] &&
          c1 == hc[(1 - ori + 3) % 3] &&
          c2 == hc[(2 - ori + 3) % 3]
        ) {
          return true
        }
      }
    }
    return false
  }

  /**
   * Fixes Red <-> Orange on scanned edges sharing the same partner color {WHITE, YELLOW, GREEN, BLUE}.
   */
  private fun fixScannedWarmEdgePairs(
    stickers: MutableList<CubeColor>,
    capturedFaces: Set<CubeFace>,
    warmOrangenessScores: FloatArray?,
  ): Int {
    var fixes = 0
    for (partner in PARTNER_COLORS) {
      val warmFacelets = ArrayList<Int>(2)
      for (e in 0 until 12) {
        val f = CubeSolver.EDGE_FACELETS[e]
        if (faceOf(f[0]) !in capturedFaces || faceOf(f[1]) !in capturedFaces) continue
        val c0 = stickers[f[0]]
        val c1 = stickers[f[1]]
        if (c0 == partner && isWarm(c1)) {
          warmFacelets.add(f[1])
        } else if (c1 == partner && isWarm(c0)) {
          warmFacelets.add(f[0])
        }
      }

      if (warmFacelets.size == 2) {
        val w0 = warmFacelets[0]
        val w1 = warmFacelets[1]
        val col0 = stickers[w0]
        val col1 = stickers[w1]
        val s0 = warmOrangenessScores?.getOrNull(w0)
        val s1 = warmOrangenessScores?.getOrNull(w1)

        if (col0 == col1) {
          // Physically impossible for both (partner, warm) edges to have the same warm color!
          if (s0 != null && s1 != null && !s0.isNaN() && !s1.isNaN() && abs(s0 - s1) > 1e-4f) {
            if (s0 < s1) {
              stickers[w0] = CubeColor.RED
              stickers[w1] = CubeColor.ORANGE
            } else {
              stickers[w0] = CubeColor.ORANGE
              stickers[w1] = CubeColor.RED
            }
          } else {
            // Flip the second one so one is RED and one is ORANGE
            stickers[w1] = if (col0 == CubeColor.RED) CubeColor.ORANGE else CubeColor.RED
          }
          fixes++
        } else if (s0 != null && s1 != null && !s0.isNaN() && !s1.isNaN()) {
          // Both are different, but check if camera warm scores strongly indicate they are inverted
          if (col0 == CubeColor.ORANGE && col1 == CubeColor.RED && s1 > s0 + 0.30f) {
            stickers[w0] = CubeColor.RED
            stickers[w1] = CubeColor.ORANGE
            fixes += 2
          } else if (col0 == CubeColor.RED && col1 == CubeColor.ORANGE && s0 > s1 + 0.30f) {
            stickers[w0] = CubeColor.ORANGE
            stickers[w1] = CubeColor.RED
            fixes += 2
          }
        }
      }
    }
    return fixes
  }

  /**
   * Performs full 6-face corner chirality correction + missing 8th corner recovery +
   * exhaustive $2^4 = 16$ warm-edge partner assignment with Kociemba permutation parity verification
   * (and missing 12th edge recovery if a single edge sticker was misread).
   */
  private fun attemptFullCubeAutocorrect(
    state: CubeState,
    warmOrangenessScores: FloatArray?,
  ): Pair<CubeState, Int> {
    val allFaces = CubeFace.entries.toSet()
    val s = state.stickers.toMutableList()

    // 1. Fix all 8 corners using 3D clockwise chirality
    fixScannedCornersByChirality(s, allFaces)

    // 2. If 7 of 8 corners are valid and 1 corner has a single misread sticker, deduce the 8th corner
    deduceEighthCornerIfSingleInvalid(s)
    fixScannedCornersByChirality(s, allFaces)

    // 3. Collect the 4 warm edge pairs partnered with WHITE, YELLOW, GREEN, BLUE
    val partnerPairs = collectWarmPartnerPairs(s)

    if (partnerPairs.size == 4) {
      // Test all 2^4 = 16 assignments of (RED, ORANGE) vs (ORANGE, RED) for the 4 partner pairs
      // and pick the physically valid assignment that minimizes disagreement with camera scores/colors.
      var bestValidStickers: List<CubeColor>? = null
      var bestCost = Float.MAX_VALUE

      for (mask in 0 until 16) {
        val candidate = s.toMutableList()
        var cost = 0f

        for (pairIdx in 0 until 4) {
          val (w0, w1) = partnerPairs[pairIdx]
          val swapOrder = ((mask shr pairIdx) and 1) == 1
          val target0 = if (!swapOrder) CubeColor.RED else CubeColor.ORANGE
          val target1 = if (!swapOrder) CubeColor.ORANGE else CubeColor.RED

          candidate[w0] = target0
          candidate[w1] = target1

          cost += assignmentCost(w0, target0, s[w0], warmOrangenessScores)
          cost += assignmentCost(w1, target1, s[w1], warmOrangenessScores)
        }

        // Also allow deducing the 12th edge if 11 of 12 edges are valid and 1 non-warm edge was misread
        deduceTwelfthEdgeIfSingleInvalid(candidate)

        val candidateState = CubeState(candidate)
        if (CubeSolver.validate(candidateState) is CubeValidationResult.Valid) {
          if (cost < bestCost) {
            bestCost = cost
            bestValidStickers = candidate
          }
        }
      }

      if (bestValidStickers != null) {
        val resultState = CubeState(bestValidStickers)
        return resultState to countWarmDifferences(state, resultState)
      }
    }

    // Fallback: apply incremental edge pair fix + deduce 12th edge if 11 edges are valid,
    // then re-run the 16 warm-edge parity search if all 4 partner pairs are now present!
    fixScannedWarmEdgePairs(s, allFaces, warmOrangenessScores)
    deduceTwelfthEdgeIfSingleInvalid(s)
    val recoveredPairs = collectWarmPartnerPairs(s)
    if (recoveredPairs.size == 4) {
      for (mask in 0 until 16) {
        val candidate = s.toMutableList()
        for (pairIdx in 0 until 4) {
          val (w0, w1) = recoveredPairs[pairIdx]
          val swapOrder = ((mask shr pairIdx) and 1) == 1
          candidate[w0] = if (!swapOrder) CubeColor.RED else CubeColor.ORANGE
          candidate[w1] = if (!swapOrder) CubeColor.ORANGE else CubeColor.RED
        }
        val candidateState = CubeState(candidate)
        if (CubeSolver.validate(candidateState) is CubeValidationResult.Valid) {
          return candidateState to countWarmDifferences(state, candidateState)
        }
      }
    }

    val resultState = CubeState(s)
    return resultState to countWarmDifferences(state, resultState)
  }

  private fun collectWarmPartnerPairs(s: List<CubeColor>): List<Pair<Int, Int>> {
    val partnerPairs = ArrayList<Pair<Int, Int>>(4)
    for (partner in PARTNER_COLORS) {
      val warmFacelets = ArrayList<Int>(2)
      for (e in 0 until 12) {
        val f = CubeSolver.EDGE_FACELETS[e]
        val c0 = s[f[0]]
        val c1 = s[f[1]]
        if (c0 == partner && isWarm(c1)) {
          warmFacelets.add(f[1])
        } else if (c1 == partner && isWarm(c0)) {
          warmFacelets.add(f[0])
        }
      }
      if (warmFacelets.size == 2) {
        partnerPairs.add(warmFacelets[0] to warmFacelets[1])
      }
    }
    return partnerPairs
  }

  private fun assignmentCost(
    stickerIdx: Int,
    assignedColor: CubeColor,
    detectedColor: CubeColor,
    warmScores: FloatArray?,
  ): Float {
    val baseFlipPenalty = if (assignedColor == detectedColor) 0f else 1.0f
    val rawScore = warmScores?.getOrNull(stickerIdx)
    if (rawScore == null || rawScore.isNaN()) {
      return baseFlipPenalty
    }
    // rawScore < 0 means camera saw Red; rawScore > 0 means camera saw Orange
    val scorePenalty = when (assignedColor) {
      CubeColor.RED -> (rawScore + 0.15f).coerceAtLeast(0f) * 1.5f
      CubeColor.ORANGE -> (-rawScore + 0.15f).coerceAtLeast(0f) * 1.5f
      else -> 0f
    }
    return baseFlipPenalty * 0.4f + scorePenalty
  }

  /**
   * If 7 of the 8 corners match 7 distinct physical corners in `0..7`, the 8th corner cubie and its
   * orientation are uniquely determined by the missing corner index and $\sum co_i \equiv 0 \pmod 3$.
   */
  private fun deduceEighthCornerIfSingleInvalid(stickers: MutableList<CubeColor>) {
    val matchedCorner = IntArray(8) { -1 }
    val matchedOri = IntArray(8)
    val usedCount = IntArray(8)

    for (i in 0 until 8) {
      val f = CubeSolver.CORNER_FACELETS[i]
      val c0 = stickers[f[0]]
      val c1 = stickers[f[1]]
      val c2 = stickers[f[2]]
      for (j in 0 until 8) {
        val hc = CubeSolver.CORNER_COLORS[j]
        for (ori in 0..2) {
          if (c0 == hc[(0 - ori + 3) % 3] &&
            c1 == hc[(1 - ori + 3) % 3] &&
            c2 == hc[(2 - ori + 3) % 3]
          ) {
            matchedCorner[i] = j
            matchedOri[i] = ori
            usedCount[j]++
            break
          }
        }
        if (matchedCorner[i] != -1) break
      }
    }

    val invalidPositions = (0 until 8).filter { pos ->
      val mc = matchedCorner[pos]
      mc == -1 || usedCount[mc] > 1
    }
    val missingPieces = (0 until 8).filter { usedCount[it] == 0 }

    if (invalidPositions.size == 1 && missingPieces.size == 1) {
      val badPos = invalidPositions[0]
      val missingPiece = missingPieces[0]
      val sumOtherOri = (0 until 8).filter { it != badPos }.sumOf { matchedOri[it] }
      val requiredOri = (3 - (sumOtherOri % 3)) % 3
      val f = CubeSolver.CORNER_FACELETS[badPos]
      val hc = CubeSolver.CORNER_COLORS[missingPiece]
      stickers[f[0]] = hc[(0 - requiredOri + 3) % 3]
      stickers[f[1]] = hc[(1 - requiredOri + 3) % 3]
      stickers[f[2]] = hc[(2 - requiredOri + 3) % 3]
    }
  }

  /**
   * If 11 of the 12 edges match 11 distinct physical edges in `0..11`, the 12th edge and its
   * orientation are uniquely determined by the missing edge index and $\sum eo_i \equiv 0 \pmod 2$.
   */
  private fun deduceTwelfthEdgeIfSingleInvalid(stickers: MutableList<CubeColor>) {
    val matchedEdge = IntArray(12) { -1 }
    val matchedOri = IntArray(12)
    val usedCount = IntArray(12)

    for (i in 0 until 12) {
      val f = CubeSolver.EDGE_FACELETS[i]
      val c0 = stickers[f[0]]
      val c1 = stickers[f[1]]
      for (j in 0 until 12) {
        val he = CubeSolver.EDGE_COLORS[j]
        if (c0 == he[0] && c1 == he[1]) {
          matchedEdge[i] = j
          matchedOri[i] = 0
          usedCount[j]++
          break
        } else if (c0 == he[1] && c1 == he[0]) {
          matchedEdge[i] = j
          matchedOri[i] = 1
          usedCount[j]++
          break
        }
      }
    }

    val invalidPositions = (0 until 12).filter { pos ->
      val me = matchedEdge[pos]
      me == -1 || usedCount[me] > 1
    }
    val missingPieces = (0 until 12).filter { usedCount[it] == 0 }

    if (invalidPositions.size == 1 && missingPieces.size == 1) {
      val badPos = invalidPositions[0]
      val missingPiece = missingPieces[0]
      val sumOtherOri = (0 until 12).filter { it != badPos }.sumOf { matchedOri[it] }
      val requiredOri = sumOtherOri and 1
      val f = CubeSolver.EDGE_FACELETS[badPos]
      val he = CubeSolver.EDGE_COLORS[missingPiece]
      stickers[f[0]] = he[requiredOri]
      stickers[f[1]] = he[1 - requiredOri]
    }
  }

  private fun countWarmDifferences(before: CubeState, after: CubeState): Int {
    var diff = 0
    for (i in 0 until 54) {
      if (before.stickers[i] != after.stickers[i]) {
        diff++
      }
    }
    return diff
  }
}

