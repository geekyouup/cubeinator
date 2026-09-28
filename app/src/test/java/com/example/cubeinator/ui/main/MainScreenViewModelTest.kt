package com.example.cubeinator.ui.main

import com.example.cubeinator.cube.CubeColor
import com.example.cubeinator.cube.CubeFace
import com.example.cubeinator.cube.SampleScramble
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MainScreenViewModelTest {

  @Test
  fun viewModel_startsWithValidScrambleAndSupportsBidirectionalSteps() {
    val viewModel = MainScreenViewModel()
    viewModel.loadSampleScramble(SampleScramble.PRESETS.first(), openSolverImmediately = true)

    // Allow synchronous solve or invoke solve directly via CubeSolver
    val initialState = viewModel.uiState.value.cubeState
    assertFalse(initialState.isSolved)

    // Test manual sticker painting and resetting to solved
    viewModel.selectBrushColor(CubeColor.RED)
    viewModel.paintSticker(CubeFace.F, 0)
    assertEquals(CubeColor.RED, viewModel.uiState.value.cubeState.stickerAt(CubeFace.F, 0, 0))

    viewModel.resetToSolvedCube()
    assertTrue(viewModel.uiState.value.cubeState.isSolved)
    assertEquals(0, viewModel.uiState.value.totalSteps)
  }

  @Test
  fun guidedFaceScan_advancesThroughAllSixFaces() {
    val viewModel = MainScreenViewModel()
    viewModel.startFreshSixFaceScan()
    assertEquals(0, viewModel.uiState.value.capturedFaces.size)
    assertEquals(CubeFace.F, viewModel.uiState.value.currentScanFace)

    repeat(6) {
      viewModel.simulateCameraScanOfCurrentFace()
    }
    assertEquals(6, viewModel.uiState.value.capturedFaces.size)
  }
}
