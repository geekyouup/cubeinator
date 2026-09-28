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
}
