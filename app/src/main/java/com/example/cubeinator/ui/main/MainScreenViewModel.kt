package com.example.cubeinator.ui.main

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.cubeinator.camera.ReticleDetectionResult
import com.example.cubeinator.cube.CubeColor
import com.example.cubeinator.cube.CubeFace
import com.example.cubeinator.cube.CubeMove
import com.example.cubeinator.cube.CubeSolver
import com.example.cubeinator.cube.CubeState
import com.example.cubeinator.cube.CubeValidationResult
import com.example.cubeinator.cube.SampleScramble
import com.example.cubeinator.cube.SolveResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.random.Random

enum class AppTab {
  SCANNER,
  SOLVER,
}

enum class VisualizerMode(val label: String) {
  CUBE_3D_ONLY("3D Cube"),
  SPLIT_3D_AND_2D("3D + 2D Net"),
  NET_2D_ONLY("2D Net"),
}

enum class StepDirection {
  NONE,
  FORWARD,
  BACKWARD,
}

data class CubeinatorUiState(
  val activeTab: AppTab = AppTab.SCANNER,
  val cubeState: CubeState = SampleScramble.PRESETS.first().buildState(),
  val activeScrambleTitle: String = SampleScramble.PRESETS.first().title,
  val scanOrderIndex: Int = 0,
  val capturedFaces: Set<CubeFace> = CubeFace.entries.toSet(),
  val liveReticleColors: List<CubeColor>? = null,
  val stabilityProgress: Float = 0f,
  val autoSnapEnabled: Boolean = false,
  val redOrangeBiasDegrees: Float = 0f,
  val selectedBrushColor: CubeColor = CubeColor.GREEN,
  val validationResult: CubeValidationResult = CubeValidationResult.Valid,
  val isSolving: Boolean = false,
  val solveResult: SolveResult? = null,
  val currentStep: Int = 0,
  val stepDirection: StepDirection = StepDirection.NONE,
  val animationTriggerId: Long = 0L,
  val isPlaying: Boolean = false,
  val playbackSpeed: Float = 1.0f,
  val visualizerMode: VisualizerMode = VisualizerMode.CUBE_3D_ONLY,
  val cameraYaw: Float = -34f,
  val cameraPitch: Float = 25f,
) {
  val currentScanFace: CubeFace
    get() = CubeFace.GUIDED_SCAN_ORDER[scanOrderIndex.coerceIn(0, 5)]

  val totalSteps: Int
    get() = solveResult?.totalSteps ?: 0

  val displayedCubeState: CubeState
    get() {
      val res = solveResult ?: return cubeState
      return res.statesAtEachStep.getOrElse(currentStep.coerceIn(0, res.totalSteps)) { cubeState }
    }

  /**
   * The move that was just applied (when at currentStep > 0) or next move to apply.
   */
  val currentMoveForStep: CubeMove?
    get() {
      val res = solveResult ?: return null
      if (res.moves.isEmpty()) return null
      return when {
        currentStep < res.totalSteps -> res.moves[currentStep]
        else -> res.moves.lastOrNull()
      }
    }

  /**
   * The move that produced the transition into [currentStep].
   */
  val lastAppliedMove: CubeMove?
    get() {
      val res = solveResult ?: return null
      return if (currentStep in 1..res.totalSteps) res.moves[currentStep - 1] else null
    }
}

class MainScreenViewModel : ViewModel() {

  private val _uiState = MutableStateFlow(CubeinatorUiState())
  val uiState: StateFlow<CubeinatorUiState> = _uiState.asStateFlow()

  private val recentFrameWindow = ArrayDeque<List<CubeColor>>()
  private val manualReticleOverrides = mutableMapOf<Int, CubeColor>()
  private var lastCandidateColors: List<CubeColor>? = null
  private var consecutiveStableFrames: Int = 0
  private var lastAutoSnapTimestampMs: Long = 0L
  private var lastCenterRgbHex: Int? = null

  init {
    // Warm up Kociemba pruning tables and pre-solve initial sample scramble on background thread
    viewModelScope.launch(Dispatchers.Default) {
      CubeSolver.warmUp()
      val initial = _uiState.value.cubeState
      val validation = CubeSolver.validate(initial)
      if (validation is CubeValidationResult.Valid) {
        val solved = CubeSolver.solve(initial)
        _uiState.update {
          it.copy(
            validationResult = validation,
            solveResult = solved,
          )
        }
      }
    }
  }

  fun selectTab(tab: AppTab) {
    if (tab == AppTab.SOLVER && _uiState.value.solveResult == null) {
      solveCurrentCube(navigateToSolver = true)
    } else {
      _uiState.update { it.copy(activeTab = tab, isPlaying = false) }
    }
  }

  fun selectScanFace(face: CubeFace) {
    val index = CubeFace.GUIDED_SCAN_ORDER.indexOf(face).coerceAtLeast(0)
    consecutiveStableFrames = 0
    recentFrameWindow.clear()
    manualReticleOverrides.clear()
    _uiState.update {
      it.copy(
        scanOrderIndex = index,
        selectedBrushColor = face.defaultColor,
        stabilityProgress = 0f,
      )
    }
  }

  fun toggleAutoSnap() {
    _uiState.update { it.copy(autoSnapEnabled = !it.autoSnapEnabled, stabilityProgress = 0f) }
    consecutiveStableFrames = 0
  }

  fun adjustRedOrangeBias(newBiasDegrees: Float) {
    val clamped = newBiasDegrees.coerceIn(-10f, 10f)
    com.example.cubeinator.camera.CubeColorDetector.redOrangeBiasDegrees = clamped
    recentFrameWindow.clear()
    _uiState.update { it.copy(redOrangeBiasDegrees = clamped) }
  }

  fun onReticleCellTap(indexInFace: Int) {
    if (indexInFace !in 0..8 || indexInFace == 4) return
    val state = _uiState.value
    val face = state.currentScanFace
    val currentColors = (state.liveReticleColors ?: state.cubeState.faceStickers(face)).toMutableList()
    val existing = currentColors[indexInFace]
    // Quick toggle Red <-> Orange if currently Red or Orange; otherwise apply selected brush color
    val nextColor = when (existing) {
      CubeColor.RED -> CubeColor.ORANGE
      CubeColor.ORANGE -> CubeColor.RED
      else -> if (state.selectedBrushColor != existing) {
        state.selectedBrushColor
      } else {
        CubeColor.entries[(existing.ordinal + 1) % CubeColor.entries.size]
      }
    }
    manualReticleOverrides[indexInFace] = nextColor
    currentColors[indexInFace] = nextColor
    val updatedCube = state.cubeState.withSticker(face, indexInFace, nextColor)
    val validation = CubeSolver.validate(updatedCube)
    _uiState.update {
      it.copy(
        liveReticleColors = currentColors,
        cubeState = updatedCube,
        validationResult = validation,
        solveResult = null,
      )
    }
  }

  fun onCameraFrameAnalyzed(result: ReticleDetectionResult) {
    val currentState = _uiState.value
    val targetFace = currentState.currentScanFace
    if (result.sampledRgbHex.size >= 5) {
      lastCenterRgbHex = result.sampledRgbHex[4]
    }

    // Rolling 5-frame majority vote per sticker to eliminate single-frame Red/Orange flicker
    if (recentFrameWindow.size >= 5) {
      recentFrameWindow.removeFirst()
    }
    recentFrameWindow.addLast(result.colors)

    val smoothedColors = MutableList(9) { cellIdx ->
      if (cellIdx == 4) {
        targetFace.defaultColor
      } else {
        manualReticleOverrides[cellIdx]
          ?: recentFrameWindow
            .groupingBy { it[cellIdx] }
            .eachCount()
            .maxByOrNull { it.value }
            ?.key
          ?: result.colors[cellIdx]
      }
    }

    if (smoothedColors == lastCandidateColors) {
      consecutiveStableFrames++
    } else {
      lastCandidateColors = smoothedColors
      consecutiveStableFrames = 1
    }

    val requiredFrames = 8
    val stability = (consecutiveStableFrames.toFloat() / requiredFrames).coerceIn(0f, 1f)
    _uiState.update {
      it.copy(
        liveReticleColors = smoothedColors,
        stabilityProgress = stability,
      )
    }

    val now = System.currentTimeMillis()
    if (currentState.autoSnapEnabled &&
      stability >= 1f &&
      now - lastAutoSnapTimestampMs > 1400L
    ) {
      lastAutoSnapTimestampMs = now
      consecutiveStableFrames = 0
      captureCurrentFace(smoothedColors)
    }
  }

  fun captureCurrentFace(overrideColors: List<CubeColor>? = null) {
    val state = _uiState.value
    val face = state.currentScanFace
    // Record center sticker calibration for this face's color
    lastCenterRgbHex?.let { rgbHex ->
      com.example.cubeinator.camera.CubeColorDetector.recordCalibratedCenter(face.defaultColor, rgbHex)
    }
    val colorsToSave = (overrideColors ?: state.liveReticleColors ?: state.cubeState.faceStickers(face))
      .toMutableList()
      .apply { this[4] = face.defaultColor }

    manualReticleOverrides.clear()
    recentFrameWindow.clear()

    val updatedCube = state.cubeState.withFace(face, colorsToSave)
    val updatedCaptured = state.capturedFaces + face
    val nextOrderIdx = if (state.scanOrderIndex < 5) state.scanOrderIndex + 1 else state.scanOrderIndex
    val nextFace = CubeFace.GUIDED_SCAN_ORDER[nextOrderIdx]
    val validation = CubeSolver.validate(updatedCube)

    _uiState.update {
      it.copy(
        cubeState = updatedCube,
        activeScrambleTitle = "Camera Scanned Cube",
        capturedFaces = updatedCaptured,
        scanOrderIndex = nextOrderIdx,
        selectedBrushColor = nextFace.defaultColor,
        stabilityProgress = 0f,
        validationResult = validation,
        solveResult = null,
      )
    }
  }

  /**
   * Simulates scanning all 6 faces with the camera using a realistic scramble
   * (useful on emulators or when testing without a physical cube).
   */
  fun simulateCameraScanOfCurrentFace() {
    val state = _uiState.value
    val face = state.currentScanFace
    val simulatedColors = state.cubeState.faceStickers(face)
    _uiState.update {
      it.copy(
        liveReticleColors = simulatedColors,
        stabilityProgress = 1f,
      )
    }
    captureCurrentFace(simulatedColors)
  }

  fun startFreshSixFaceScan() {
    consecutiveStableFrames = 0
    recentFrameWindow.clear()
    manualReticleOverrides.clear()
    com.example.cubeinator.camera.CubeColorDetector.clearCalibration()
    _uiState.update {
      it.copy(
        scanOrderIndex = 0,
        capturedFaces = emptySet(),
        stabilityProgress = 0f,
        selectedBrushColor = CubeFace.GUIDED_SCAN_ORDER[0].defaultColor,
        isPlaying = false,
      )
    }
  }

  fun selectBrushColor(color: CubeColor) {
    _uiState.update { it.copy(selectedBrushColor = color) }
  }

  fun paintSticker(face: CubeFace, indexInFace: Int) {
    // Center sticker (index 4) defines the face identity and stays fixed to face.defaultColor
    if (indexInFace == 4) return
    val state = _uiState.value
    val updatedCube = state.cubeState.withSticker(face, indexInFace, state.selectedBrushColor)
    val validation = CubeSolver.validate(updatedCube)
    _uiState.update {
      it.copy(
        cubeState = updatedCube,
        activeScrambleTitle = "Custom Edited Cube",
        validationResult = validation,
        solveResult = null,
      )
    }
  }

  fun loadSampleScramble(preset: SampleScramble, openSolverImmediately: Boolean = false) {
    val scrambled = preset.buildState()
    val validation = CubeSolver.validate(scrambled)
    _uiState.update {
      it.copy(
        cubeState = scrambled,
        activeScrambleTitle = preset.title,
        capturedFaces = CubeFace.entries.toSet(),
        validationResult = validation,
        currentStep = 0,
        stepDirection = StepDirection.NONE,
        isPlaying = false,
      )
    }
    solveCurrentCube(navigateToSolver = openSolverImmediately)
  }

  fun generateRandomScramble(openSolverImmediately: Boolean = false) {
    val allMoves = CubeMove.entries
    val picked = ArrayList<CubeMove>(20)
    var lastFace: CubeFace? = null
    repeat(20) {
      val candidates = allMoves.filter { it.face != lastFace }
      val move = candidates[Random.nextInt(candidates.size)]
      picked.add(move)
      lastFace = move.face
    }
    val scrambled = CubeState.solved().applyMoves(picked)
    val validation = CubeSolver.validate(scrambled)
    _uiState.update {
      it.copy(
        cubeState = scrambled,
        activeScrambleTitle = "Random 20-Move Scramble",
        capturedFaces = CubeFace.entries.toSet(),
        validationResult = validation,
        currentStep = 0,
        stepDirection = StepDirection.NONE,
        isPlaying = false,
      )
    }
    solveCurrentCube(navigateToSolver = openSolverImmediately)
  }

  fun resetToSolvedCube() {
    val solved = CubeState.solved()
    _uiState.update {
      it.copy(
        cubeState = solved,
        activeScrambleTitle = "Solved Cube",
        capturedFaces = CubeFace.entries.toSet(),
        validationResult = CubeValidationResult.Valid,
        solveResult = SolveResult(emptyList(), listOf(solved), 0L),
        currentStep = 0,
        stepDirection = StepDirection.NONE,
        isPlaying = false,
      )
    }
  }

  fun solveCurrentCube(navigateToSolver: Boolean = true) {
    val currentCube = _uiState.value.cubeState
    val validation = CubeSolver.validate(currentCube)
    if (validation is CubeValidationResult.Invalid) {
      _uiState.update { it.copy(validationResult = validation) }
      return
    }

    _uiState.update {
      it.copy(
        isSolving = true,
        validationResult = CubeValidationResult.Valid,
        isPlaying = false,
      )
    }

    viewModelScope.launch(Dispatchers.Default) {
      val result = CubeSolver.solve(currentCube)
      _uiState.update {
        it.copy(
          isSolving = false,
          solveResult = result,
          currentStep = 0,
          stepDirection = StepDirection.NONE,
          activeTab = if (navigateToSolver) AppTab.SOLVER else it.activeTab,
        )
      }
    }
  }

  /**
   * Advances one step forward in the solution and triggers a forward 3D face rotation animation.
   */
  fun stepForward() {
    val state = _uiState.value
    val res = state.solveResult ?: return
    if (state.currentStep < res.totalSteps) {
      _uiState.update {
        it.copy(
          currentStep = it.currentStep + 1,
          stepDirection = StepDirection.FORWARD,
          animationTriggerId = it.animationTriggerId + 1L,
        )
      }
    } else {
      _uiState.update { it.copy(isPlaying = false) }
    }
  }

  /**
   * Steps one move backward in the solution and triggers the inverse 3D face rotation animation.
   */
  fun stepBackward() {
    val state = _uiState.value
    if (state.currentStep > 0) {
      _uiState.update {
        it.copy(
          currentStep = it.currentStep - 1,
          stepDirection = StepDirection.BACKWARD,
          animationTriggerId = it.animationTriggerId + 1L,
          isPlaying = false,
        )
      }
    }
  }

  /**
   * Jumps or scrubs to a specific step in [0..totalSteps].
   */
  fun seekToStep(targetStep: Int) {
    val state = _uiState.value
    val res = state.solveResult ?: return
    val clamped = targetStep.coerceIn(0, res.totalSteps)
    if (clamped == state.currentStep) return

    val dir = when {
      clamped == state.currentStep + 1 -> StepDirection.FORWARD
      clamped == state.currentStep - 1 -> StepDirection.BACKWARD
      else -> StepDirection.NONE
    }
    _uiState.update {
      it.copy(
        currentStep = clamped,
        stepDirection = dir,
        animationTriggerId = if (dir != StepDirection.NONE) it.animationTriggerId + 1L else it.animationTriggerId,
        isPlaying = false,
      )
    }
  }

  fun togglePlayPause() {
    val state = _uiState.value
    val res = state.solveResult ?: return
    if (res.totalSteps == 0) return

    if (!state.isPlaying && state.currentStep >= res.totalSteps) {
      // Restart from step 0 when pressing Play at the end
      _uiState.update {
        it.copy(
          currentStep = 0,
          stepDirection = StepDirection.NONE,
          isPlaying = true,
        )
      }
    } else {
      _uiState.update { it.copy(isPlaying = !it.isPlaying) }
    }
  }

  fun setPlaybackSpeed(speed: Float) {
    _uiState.update { it.copy(playbackSpeed = speed) }
  }

  fun setVisualizerMode(mode: VisualizerMode) {
    _uiState.update { it.copy(visualizerMode = mode) }
  }

  fun updateCameraOrbit(deltaYaw: Float, deltaPitch: Float) {
    _uiState.update {
      it.copy(
        cameraYaw = (it.cameraYaw + deltaYaw) % 360f,
        cameraPitch = (it.cameraPitch + deltaPitch).coerceIn(-75f, 75f),
      )
    }
  }

  fun resetCameraOrbit() {
    _uiState.update { it.copy(cameraYaw = -34f, cameraPitch = 25f) }
  }

  fun focusCameraOnFace(face: CubeFace) {
    val (targetYaw, targetPitch) = when (face) {
      CubeFace.F -> -22f to 20f
      CubeFace.R -> -68f to 20f
      CubeFace.B -> 158f to 20f
      CubeFace.L -> 68f to 20f
      CubeFace.U -> -25f to 55f
      CubeFace.D -> -25f to -55f
    }
    _uiState.update { it.copy(cameraYaw = targetYaw, cameraPitch = targetPitch) }
  }
}
