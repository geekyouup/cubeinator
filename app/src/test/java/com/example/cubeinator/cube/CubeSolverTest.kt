package com.example.cubeinator.cube

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class CubeSolverTest {

  @Test
  fun solvedCube_isSolvedAndZeroMoves() {
    val solved = CubeState.solved()
    assertTrue(solved.isSolved)
    assertEquals(CubeValidationResult.Valid, CubeSolver.validate(solved))
    val res = CubeSolver.solve(solved)
    assertTrue(res.moves.isEmpty())
    assertEquals(1, res.statesAtEachStep.size)
  }

  @Test
  fun everyMoveFollowedByInverse_returnsSolved() {
    val solved = CubeState.solved()
    for (move in CubeMove.entries) {
      val moved = solved.applyMove(move)
      assertTrue("Move $move should change solved cube", !moved.isSolved)
      assertEquals(
        "Cube after $move should be physically valid",
        CubeValidationResult.Valid,
        CubeSolver.validate(moved),
      )
      val restored = moved.applyMove(move.inverse())
      assertEquals("Move $move followed by ${move.inverse()} must restore solved", solved, restored)
    }
  }

  @Test
  fun sexyMoveSixTimes_returnsSolved() {
    // (R U R' U')^6 == Identity on any 3x3x3 Rubik's Cube
    val trigger = CubeMove.parseSequence("R U R' U'")
    var state = CubeState.solved()
    repeat(6) {
      state = state.applyMoves(trigger)
    }
    assertEquals(CubeState.solved(), state)
  }

  @Test
  fun sampleScrambles_solveQuicklyToSolvedState() {
    for (preset in SampleScramble.PRESETS) {
      val scrambled = preset.buildState()
      assertEquals(
        "Preset '${preset.title}' must be valid",
        CubeValidationResult.Valid,
        CubeSolver.validate(scrambled),
      )
      val solution = CubeSolver.solve(scrambled)
      assertTrue(
        "Solution for '${preset.title}' should be <= 22 moves, got ${solution.totalSteps}",
        solution.totalSteps in 1..22,
      )
      val finalState = solution.statesAtEachStep.last()
      assertTrue("Final state for '${preset.title}' must be solved", finalState.isSolved)

      // Verify stepping backward from solved to initial state
      for (step in solution.totalSteps downTo 1) {
        val stateAtStep = solution.statesAtEachStep[step]
        val moveAtStep = solution.moves[step - 1]
        val prevState = stateAtStep.applyMove(moveAtStep.inverse())
        assertEquals(solution.statesAtEachStep[step - 1], prevState)
      }
    }
  }

  @Test
  fun random25MoveScrambles_allSolveOptimally() {
    val rng = Random(42)
    val allMoves = CubeMove.entries
    repeat(10) { iter ->
      var state = CubeState.solved()
      repeat(25) {
        state = state.applyMove(allMoves[rng.nextInt(allMoves.size)])
      }
      assertEquals(CubeValidationResult.Valid, CubeSolver.validate(state))
      val result = CubeSolver.solve(state)
      assertTrue("Iteration $iter solution must solve the cube", result.statesAtEachStep.last().isSolved)
      assertTrue("Iteration $iter solution length ${result.totalSteps} <= 22", result.totalSteps <= 22)
    }
  }

  @Test
  fun cornerChirality_autocorrectsAllEightCornersWhenRedAndOrangeAreSwapped() {
    val rng = Random(123)
    val allMoves = CubeMove.entries
    val allFaces = CubeFace.entries.toSet()

    repeat(15) {
      var trueState = CubeState.solved()
      repeat(20) {
        trueState = trueState.applyMove(allMoves[rng.nextInt(allMoves.size)])
      }

      // Deliberately flip RED <-> ORANGE on ALL 8 corners!
      val corrupted = trueState.stickers.toMutableList()
      for (cornerFacelets in CubeSolver.CORNER_FACELETS) {
        for (idx in cornerFacelets) {
          if (corrupted[idx] == CubeColor.RED) {
            corrupted[idx] = CubeColor.ORANGE
          } else if (corrupted[idx] == CubeColor.ORANGE) {
            corrupted[idx] = CubeColor.RED
          }
        }
      }

      val outcome = CubeLayoutCorrector.autocorrect(
        rawState = CubeState(corrupted),
        capturedFaces = allFaces,
      )
      assertEquals(8, outcome.redOrangeFixesCount)
      assertEquals(trueState, outcome.cubeState)
      assertEquals(CubeValidationResult.Valid, CubeSolver.validate(outcome.cubeState))
    }
  }

  @Test
  fun edgePartnerAndParity_autocorrectsRedOrangeEdgeAndCornerMisclassifications() {
    val trueState = SampleScramble.PRESETS[1].buildState()
    val allFaces = CubeFace.entries.toSet()
    val warmScores = FloatArray(54) { idx ->
      when (trueState.stickers[idx]) {
        CubeColor.RED -> -0.45f
        CubeColor.ORANGE -> +0.45f
        else -> Float.NaN
      }
    }

    // Corrupt 4 warm corner stickers + 3 warm edge stickers (making duplicate RED/ORANGE edges)
    val corrupted = trueState.stickers.toMutableList()
    var flippedCount = 0
    for (i in 0 until 54) {
      if (i % 9 == 4) continue // keep centers
      if (corrupted[i] == CubeColor.RED && flippedCount < 5) {
        corrupted[i] = CubeColor.ORANGE
        flippedCount++
      } else if (corrupted[i] == CubeColor.ORANGE && flippedCount < 7) {
        corrupted[i] = CubeColor.RED
        flippedCount++
      }
    }

    val outcome = CubeLayoutCorrector.autocorrect(
      rawState = CubeState(corrupted),
      capturedFaces = allFaces,
      warmOrangenessScores = warmScores,
    )
    assertTrue(outcome.redOrangeFixesCount > 0)
    assertEquals(CubeValidationResult.Valid, CubeSolver.validate(outcome.cubeState))
    assertEquals(trueState, outcome.cubeState)
  }

  @Test
  fun rotatedTopAndBottomFacesWithRedOrangeErrors_areAutomaticallyAlignedAndCorrected() {
    val trueState = SampleScramble.PRESETS[0].buildState()
    // Simulate user holding U rotated 90° CW and D rotated 180° while scanning, plus flipping a corner Red->Orange
    var scanned = trueState
      .withFace(CubeFace.U, CubeLayoutCorrector.rotateFaceStickersClockwise(trueState.faceStickers(CubeFace.U), 1))
      .withFace(CubeFace.D, CubeLayoutCorrector.rotateFaceStickersClockwise(trueState.faceStickers(CubeFace.D), 2))

    val s = scanned.stickers.toMutableList()
    // Flip warm sticker on corner 0 (URF)
    for (idx in CubeSolver.CORNER_FACELETS[0]) {
      if (s[idx] == CubeColor.RED) s[idx] = CubeColor.ORANGE
      else if (s[idx] == CubeColor.ORANGE) s[idx] = CubeColor.RED
    }
    scanned = CubeState(s)

    val outcome = CubeLayoutCorrector.autocorrect(
      rawState = scanned,
      capturedFaces = CubeFace.entries.toSet(),
    )
    assertEquals(CubeValidationResult.Valid, CubeSolver.validate(outcome.cubeState))
    assertEquals(trueState, outcome.cubeState)
  }

  @Test
  fun partialScan_detectsAndFixesUpsideDownFaceWhileScanning() {
    val trueState = SampleScramble.PRESETS[0].buildState()
    // Simulate scanning F upright, then scanning U upside-down (180°) and R rotated 90°
    val upsideDownU = CubeLayoutCorrector.rotateFaceStickersClockwise(trueState.faceStickers(CubeFace.U), 2)
    val rotatedR = CubeLayoutCorrector.rotateFaceStickersClockwise(trueState.faceStickers(CubeFace.R), 1)

    val partialRaw = CubeState.solved()
      .withFace(CubeFace.F, trueState.faceStickers(CubeFace.F))
      .withFace(CubeFace.U, upsideDownU)
      .withFace(CubeFace.R, rotatedR)

    val captured = setOf(CubeFace.F, CubeFace.U, CubeFace.R)
    // Before autocorrection, the upside-down U and rotated R clash on shared edges/corners
    assertTrue(CubeLayoutCorrector.computePartialValidityPenalty(partialRaw.stickers, captured) > 0)

    val outcome = CubeLayoutCorrector.autocorrect(
      rawState = partialRaw,
      capturedFaces = captured,
      mostRecentFace = CubeFace.R,
    )
    // After incremental autocorrection, all shared edges and corners among F, U, R have 0 clashes!
    assertEquals(0, CubeLayoutCorrector.computePartialValidityPenalty(outcome.cubeState.stickers, captured))
    assertTrue(outcome.rotatedFacesCount >= 1)
  }

  @Test
  fun multipleUpsideDownAndSidewaysFacesAcrossCube_areAllAutomaticallyFixed() {
    val trueState = SampleScramble.PRESETS[1].buildState()
    // Simulate user scanning B upside down (180°), L rotated 270°, U rotated 90°, plus Red/Orange corner errors
    var scanned = trueState
      .withFace(CubeFace.B, CubeLayoutCorrector.rotateFaceStickersClockwise(trueState.faceStickers(CubeFace.B), 2))
      .withFace(CubeFace.L, CubeLayoutCorrector.rotateFaceStickersClockwise(trueState.faceStickers(CubeFace.L), 3))
      .withFace(CubeFace.U, CubeLayoutCorrector.rotateFaceStickersClockwise(trueState.faceStickers(CubeFace.U), 1))

    val s = scanned.stickers.toMutableList()
    for (idx in CubeSolver.CORNER_FACELETS[2]) {
      if (s[idx] == CubeColor.RED) s[idx] = CubeColor.ORANGE
      else if (s[idx] == CubeColor.ORANGE) s[idx] = CubeColor.RED
    }
    scanned = CubeState(s)

    val outcome = CubeLayoutCorrector.autocorrect(
      rawState = scanned,
      capturedFaces = CubeFace.entries.toSet(),
    )
    assertEquals(CubeValidationResult.Valid, CubeSolver.validate(outcome.cubeState))
    assertEquals(trueState, outcome.cubeState)
  }
}
