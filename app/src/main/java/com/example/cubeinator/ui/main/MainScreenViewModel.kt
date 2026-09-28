package com.example.cubeinator.ui.main

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.cubeinator.camera.CubeColorDetector
import com.example.cubeinator.camera.ReticleDetectionResult
import com.example.cubeinator.cube.CubeColor
import com.example.cubeinator.cube.CubeFace
import com.example.cubeinator.cube.CubeLayoutCorrector
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
import kotlin.math.abs
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
  val capturedFaces: Set<CubeFace> = emptySet(),
  val liveDetectedFace: CubeFace? = null,
  val liveReticleColors: List<CubeColor>? = null,
  val isCubeFaceVisibleInCamera: Boolean = false,
  val stabilityProgress: Float = 0f,
  val autoSnapEnabled: Boolean = false,
  val lastAutocorrectMessage: String? = null,
  val totalAutocorrectedStickers: Int = 0,
  val redOrangeBiasDegrees: Float = 0f,
  val selectedBrushColor: CubeColor = CubeColor.GREEN,
  val validationResult: CubeValidationResult = CubeValidationResult.Valid,
  val isSolving: Boolean = false,
  val solveResult: SolveResult? = null,
  val currentStep: Int = 0,
  val stepDirection: StepDirection = StepDirection.NONE,
  val animationTriggerId: Long = 0L,
  val isPlaying: Boolean = false,
  val playbackSpeed: Float = 0.5f,
  val visualizerMode: VisualizerMode = VisualizerMode.CUBE_3D_ONLY,
  val cameraYaw: Float = -34f,
  val cameraPitch: Float = 25f,
) {
  val currentScanFace: CubeFace
    get() = CubeFace.GUIDED_SCAN_ORDER[scanOrderIndex.coerceIn(0, 5)]

  val nextUnscannedFace: CubeFace?
    get() = CubeFace.GUIDED_SCAN_ORDER.firstOrNull { it !in capturedFaces }

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

  private data class WarmFaceCapture(
    val colors: List<CubeColor>,
    val centerLabHueDeg: Float,
    val centerRgbHex: Int,
    val warmScores: FloatArray,
  )

  private val _uiState = MutableStateFlow(CubeinatorUiState())
  val uiState: StateFlow<CubeinatorUiState> = _uiState.asStateFlow()

  private val recentFrameWindow = ArrayDeque<List<CubeColor>>()
  private val manualReticleOverrides = mutableMapOf<Int, CubeColor>()
  private var lastCandidateColors: List<CubeColor>? = null
  private var consecutiveStableFrames: Int = 0
  private var lastAutoSnapTimestampMs: Long = 0L
  private var lastAutoSnappedFace: CubeFace? = null
  private var lastCenterRgbHex: Int? = null
  private var lastCenterLabHueDeg: Float = 0f
  private var lastFrameWarmScores: FloatArray = FloatArray(9) { Float.NaN }

  // Raw per-sticker state and warm scores accumulated during a 6-face camera scan
  private var rawScannedCubeState: CubeState = CubeState.solved()
  private val stickerWarmScores = FloatArray(54) { Float.NaN }
  private val warmFaceCaptures = ArrayList<WarmFaceCapture>(2)
  private var isSamplePresetLoaded: Boolean = false

  init {
    // Warm up Kociemba pruning tables and pre-solve initial scramble on background thread
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
      _uiState.update {
        it.copy(
          activeTab = tab,
          isPlaying = false,
          cameraYaw = if (tab == AppTab.SOLVER) -34f else it.cameraYaw,
          cameraPitch = if (tab == AppTab.SOLVER) 25f else it.cameraPitch,
        )
      }
    }
  }

  fun selectScanFace(face: CubeFace) {
    val index = CubeFace.GUIDED_SCAN_ORDER.indexOf(face).coerceAtLeast(0)
    val (targetYaw, targetPitch) = orbitAnglesForFace(face)
    consecutiveStableFrames = 0
    recentFrameWindow.clear()
    manualReticleOverrides.clear()
    _uiState.update {
      it.copy(
        scanOrderIndex = index,
        selectedBrushColor = face.defaultColor,
        stabilityProgress = 0f,
        cameraYaw = targetYaw,
        cameraPitch = targetPitch,
      )
    }
  }

  fun toggleAutoSnap() {
    val current = _uiState.value
    if (!current.autoSnapEnabled && current.capturedFaces.size == 6) {
      // If all 6 faces were already scanned and scanning auto-stopped, turning scanning ON starts a fresh scan
      startFreshSixFaceScan(enableScanning = true)
      return
    }
    recentFrameWindow.clear()
    consecutiveStableFrames = 0
    _uiState.update { it.copy(autoSnapEnabled = !it.autoSnapEnabled, stabilityProgress = 0f) }
  }

  fun adjustRedOrangeBias(newBiasDegrees: Float) {
    val clamped = newBiasDegrees.coerceIn(-10f, 10f)
    CubeColorDetector.redOrangeBiasDegrees = clamped
    recentFrameWindow.clear()
    _uiState.update { it.copy(redOrangeBiasDegrees = clamped) }
  }

  fun onReticleCellTap(indexInFace: Int) {
    if (indexInFace !in 0..8 || indexInFace == 4) return
    val state = _uiState.value
    val face = state.liveDetectedFace ?: state.currentScanFace
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
    rawScannedCubeState = rawScannedCubeState.withSticker(face, indexInFace, nextColor)
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

    // Wait until a real Rubik's Cube 3x3 face is visible in the reticle before buffering or scanning!
    if (!result.isFaceAligned) {
      recentFrameWindow.clear()
      consecutiveStableFrames = 0
      if (currentState.isCubeFaceVisibleInCamera || currentState.stabilityProgress > 0f) {
        _uiState.update {
          it.copy(
            isCubeFaceVisibleInCamera = false,
            stabilityProgress = 0f,
          )
        }
      }
      return
    }

    if (result.sampledRgbHex.size >= 5) {
      lastCenterRgbHex = result.sampledRgbHex[4]
    }
    lastCenterLabHueDeg = result.centerLabHueDeg
    lastFrameWarmScores = result.warmOrangenessScores

    // Rolling 5-frame majority vote per sticker (including center index 4) to eliminate flicker
    if (recentFrameWindow.size >= 5) {
      recentFrameWindow.removeFirst()
    }
    recentFrameWindow.addLast(result.colors)

    val smoothedColors = MutableList(9) { cellIdx ->
      if (cellIdx != 4 && manualReticleOverrides.containsKey(cellIdx)) {
        manualReticleOverrides.getValue(cellIdx)
      } else {
        recentFrameWindow
          .groupingBy { it[cellIdx] }
          .eachCount()
          .maxByOrNull { it.value }
          ?.key
          ?: result.colors[cellIdx]
      }
    }

    // Automatically identify which face the user is holding in front of the camera from the center sticker
    val detectedFace = resolveDetectedFaceFromCenter(
      centerColor = smoothedColors[4],
      faceColors = smoothedColors,
      centerLabHueDeg = result.centerLabHueDeg,
      capturedFaces = if (isSamplePresetLoaded) emptySet() else currentState.capturedFaces,
      fallbackTargetFace = currentState.currentScanFace,
    )
    smoothedColors[4] = detectedFace.defaultColor

    if (!currentState.autoSnapEnabled) {
      consecutiveStableFrames = 0
      _uiState.update {
        it.copy(
          liveDetectedFace = detectedFace,
          liveReticleColors = smoothedColors,
          isCubeFaceVisibleInCamera = true,
          stabilityProgress = 0f,
        )
      }
      return
    }

    if (smoothedColors == lastCandidateColors) {
      consecutiveStableFrames++
    } else {
      lastCandidateColors = smoothedColors
      consecutiveStableFrames = 1
    }

    val effectiveCaptured = if (isSamplePresetLoaded) emptySet() else currentState.capturedFaces
    val isNewFace = detectedFace !in effectiveCaptured

    // Check if the candidate face (under any 0°/90°/180°/270° rotation) is physically compatible
    // with the already-scanned faces. If a sticker clashes under all rotations (e.g. mid-turn blur or glare),
    // require more consecutive stable frames before locking in.
    val rotationPenalty = CubeLayoutCorrector.bestRotationPenaltyForCandidateFace(
      currentRawState = rawScannedCubeState,
      capturedFaces = effectiveCaptured,
      candidateFace = detectedFace,
      candidateColors = smoothedColors,
    )
    val requiredFrames = when {
      rotationPenalty > 0 -> 14
      isNewFace -> 6
      else -> 9
    }
    val stability = (consecutiveStableFrames.toFloat() / requiredFrames).coerceIn(0f, 1f)

    _uiState.update {
      it.copy(
        liveDetectedFace = detectedFace,
        liveReticleColors = smoothedColors,
        isCubeFaceVisibleInCamera = true,
        stabilityProgress = stability,
      )
    }

    val now = System.currentTimeMillis()
    val cooldownMs = if (detectedFace != lastAutoSnappedFace) 600L else 1500L
    if (currentState.autoSnapEnabled &&
      stability >= 1f &&
      now - lastAutoSnapTimestampMs > cooldownMs
    ) {
      // Only auto-capture an already-captured face if its detected colors actually changed
      val existingFaceColors = rawScannedCubeState.faceStickers(detectedFace)
      if (isNewFace || smoothedColors != existingFaceColors) {
        lastAutoSnapTimestampMs = now
        lastAutoSnappedFace = detectedFace
        consecutiveStableFrames = 0
        captureFaceInternal(
          face = detectedFace,
          overrideColors = smoothedColors,
          centerLabHueDeg = result.centerLabHueDeg,
          centerRgbHex = lastCenterRgbHex,
          faceWarmScores = result.warmOrangenessScores,
        )
      }
    }
  }

  /**
   * Resolves which [CubeFace] is in the reticle based on the detected center color and
   * smart Red (R) vs Orange (L) center disambiguation when rotating the cube.
   */
  private fun resolveDetectedFaceFromCenter(
    centerColor: CubeColor,
    faceColors: List<CubeColor>,
    centerLabHueDeg: Float,
    capturedFaces: Set<CubeFace>,
    fallbackTargetFace: CubeFace,
  ): CubeFace {
    if (centerColor != CubeColor.RED && centerColor != CubeColor.ORANGE) {
      return centerColor.homeFace
    }

    // Center is warm (RED or ORANGE). Check if we already captured one warm face in this session:
    if (warmFaceCaptures.size == 1) {
      val prev = warmFaceCaptures[0]
      if (isDistinctWarmFace(prev.colors, faceColors, prev.centerLabHueDeg, centerLabHueDeg)) {
        // This is the OTHER warm face! Compare center Lab hue angles:
        // Lower Lab hue = Red (R), Higher Lab hue = Orange (L)
        return if (centerLabHueDeg < prev.centerLabHueDeg) CubeFace.R else CubeFace.L
      } else {
        // Same warm face still in front of the camera
        return if (CubeFace.R in capturedFaces && CubeFace.L !in capturedFaces) {
          CubeFace.R
        } else if (CubeFace.L in capturedFaces && CubeFace.R !in capturedFaces) {
          CubeFace.L
        } else {
          centerColor.homeFace
        }
      }
    } else if (warmFaceCaptures.size >= 2) {
      val redHue = warmFaceCaptures.minOf { it.centerLabHueDeg }
      val orangeHue = warmFaceCaptures.maxOf { it.centerLabHueDeg }
      val mid = (redHue + orangeHue) * 0.5f
      return if (centerLabHueDeg < mid) CubeFace.R else CubeFace.L
    }

    // First warm face seen in this session
    if (fallbackTargetFace == CubeFace.R || fallbackTargetFace == CubeFace.L) {
      if (fallbackTargetFace !in capturedFaces) return fallbackTargetFace
    }
    return centerColor.homeFace
  }

  /**
   * Determines whether two warm-centered 3x3 scans represent the two opposite faces (R and L)
   * rather than the same face held in front of the camera twice.
   */
  private fun isDistinctWarmFace(
    first: List<CubeColor>,
    second: List<CubeColor>,
    hue1: Float,
    hue2: Float,
  ): Boolean {
    if (abs(hue1 - hue2) >= 6.0f) return true
    var nonWarmDiffs = 0
    for (i in 0 until 9) {
      if (i == 4) continue
      val c1 = if (CubeLayoutCorrector.isWarm(first[i])) CubeColor.RED else first[i]
      val c2 = if (CubeLayoutCorrector.isWarm(second[i])) CubeColor.RED else second[i]
      if (c1 != c2) nonWarmDiffs++
    }
    return nonWarmDiffs >= 2
  }

  fun captureCurrentFace(overrideColors: List<CubeColor>? = null) {
    val state = _uiState.value
    val face = state.liveDetectedFace ?: state.currentScanFace
    captureFaceInternal(
      face = face,
      overrideColors = overrideColors,
      centerLabHueDeg = lastCenterLabHueDeg,
      centerRgbHex = lastCenterRgbHex,
      faceWarmScores = lastFrameWarmScores,
    )
  }

  private fun captureFaceInternal(
    face: CubeFace,
    overrideColors: List<CubeColor>? = null,
    centerLabHueDeg: Float = 0f,
    centerRgbHex: Int? = null,
    faceWarmScores: FloatArray? = null,
  ) {
    // If the initial sample preset was still loaded when the user started scanning with the camera,
    // automatically transition to a fresh live scan so the 3D cube and 2D net fill in cleanly!
    if (isSamplePresetLoaded && overrideColors != null && _uiState.value.capturedFaces.size == 6) {
      isSamplePresetLoaded = false
      rawScannedCubeState = CubeState.solved()
      stickerWarmScores.fill(Float.NaN)
      warmFaceCaptures.clear()
    }

    val state = _uiState.value
    val baseCaptured = if (!isSamplePresetLoaded && state.capturedFaces.size == 6 && warmFaceCaptures.isEmpty()) {
      emptySet()
    } else {
      state.capturedFaces
    }

    val colorsToSave = (overrideColors ?: state.liveReticleColors ?: rawScannedCubeState.faceStickers(face))
      .toMutableList()
      .apply { this[4] = face.defaultColor }

    manualReticleOverrides.clear()
    recentFrameWindow.clear()

    var updatedCaptured = baseCaptured + face

    // Smart handling for warm-centered faces (R = Red and L = Orange):
    // When both warm faces have been seen, compare their center Lab hue angles so that even if
    // the first warm face was misassigned to L instead of R (or vice versa), both are placed on
    // their true faces and calibrated!
    if ((face == CubeFace.R || face == CubeFace.L) && centerLabHueDeg > 0f) {
      val scoresCopy = faceWarmScores?.clone() ?: FloatArray(9) { Float.NaN }
      val newRecord = WarmFaceCapture(
        colors = colorsToSave,
        centerLabHueDeg = centerLabHueDeg,
        centerRgbHex = centerRgbHex ?: 0,
        warmScores = scoresCopy,
      )
      val existingMatchIdx = warmFaceCaptures.indexOfFirst {
        !isDistinctWarmFace(it.colors, colorsToSave, it.centerLabHueDeg, centerLabHueDeg)
      }
      if (existingMatchIdx >= 0) {
        warmFaceCaptures[existingMatchIdx] = newRecord
      } else if (warmFaceCaptures.size < 2) {
        warmFaceCaptures.add(newRecord)
      } else {
        warmFaceCaptures[1] = newRecord
      }

      if (warmFaceCaptures.size == 2) {
        val sortedByHue = warmFaceCaptures.sortedBy { it.centerLabHueDeg }
        val redCapture = sortedByHue[0]
        val orangeCapture = sortedByHue[1]
        if (redCapture.centerRgbHex != 0) {
          CubeColorDetector.recordCalibratedCenter(CubeColor.RED, redCapture.centerRgbHex)
        }
        if (orangeCapture.centerRgbHex != 0) {
          CubeColorDetector.recordCalibratedCenter(CubeColor.ORANGE, orangeCapture.centerRgbHex)
        }
        val redColors = redCapture.colors.toMutableList().apply { this[4] = CubeColor.RED }
        val orangeColors = orangeCapture.colors.toMutableList().apply { this[4] = CubeColor.ORANGE }
        rawScannedCubeState = rawScannedCubeState
          .withFace(CubeFace.R, redColors)
          .withFace(CubeFace.L, orangeColors)
        storeFaceWarmScores(CubeFace.R, redCapture.warmScores)
        storeFaceWarmScores(CubeFace.L, orangeCapture.warmScores)
        updatedCaptured = updatedCaptured + CubeFace.R + CubeFace.L
      } else {
        centerRgbHex?.let { CubeColorDetector.recordCalibratedCenter(face.defaultColor, it) }
        rawScannedCubeState = rawScannedCubeState.withFace(face, colorsToSave)
        if (faceWarmScores != null) storeFaceWarmScores(face, faceWarmScores)
      }
    } else {
      centerRgbHex?.let { CubeColorDetector.recordCalibratedCenter(face.defaultColor, it) }
      rawScannedCubeState = rawScannedCubeState.withFace(face, colorsToSave)
      if (faceWarmScores != null) storeFaceWarmScores(face, faceWarmScores)
    }

    // Run Rubik's Cube Layout & Group-Theory Autocorrector (Face Rotations + Corner Chirality + Edge Partner + Parity)
    val autocorrectOutcome = CubeLayoutCorrector.autocorrect(
      rawState = rawScannedCubeState,
      capturedFaces = updatedCaptured,
      warmOrangenessScores = stickerWarmScores,
      mostRecentFace = face,
    )
    // Persist any auto-detected face rotations so subsequent face scans compare against upright faces!
    rawScannedCubeState = autocorrectOutcome.alignedRawState
    autocorrectOutcome.alignedWarmScores?.let { aligned ->
      for (i in 0 until minOf(stickerWarmScores.size, aligned.size)) {
        stickerWarmScores[i] = aligned[i]
      }
    }
    val correctedCube = autocorrectOutcome.cubeState

    // Advance guided scan order index to the next unscanned face (or next in sequence)
    val nextUnscannedIdx = CubeFace.GUIDED_SCAN_ORDER.indexOfFirst { it !in updatedCaptured }
    val nextOrderIdx = if (nextUnscannedIdx >= 0) {
      nextUnscannedIdx
    } else {
      ((CubeFace.GUIDED_SCAN_ORDER.indexOf(face) + 1) % 6)
    }
    val nextFace = CubeFace.GUIDED_SCAN_ORDER[nextOrderIdx]
    val (orbitYaw, orbitPitch) = orbitAnglesForFace(face)
    val validation = CubeSolver.validate(correctedCube)
    val allSixCaptured = updatedCaptured.size == 6

    _uiState.update {
      it.copy(
        cubeState = correctedCube,
        activeScrambleTitle = "Camera Auto-Detected Cube",
        capturedFaces = updatedCaptured,
        scanOrderIndex = nextOrderIdx,
        selectedBrushColor = nextFace.defaultColor,
        stabilityProgress = 0f,
        // Automatically stop auto-scanning once all 6 faces of the cube are completely scanned!
        autoSnapEnabled = if (allSixCaptured) false else it.autoSnapEnabled,
        lastAutocorrectMessage = autocorrectOutcome.summaryMessage ?: it.lastAutocorrectMessage,
        totalAutocorrectedStickers = autocorrectOutcome.redOrangeFixesCount,
        cameraYaw = orbitYaw,
        cameraPitch = orbitPitch,
        validationResult = validation,
        solveResult = null,
      )
    }

    // Automatically pre-compute optimal Kociemba solution as soon as all 6 faces are scanned and valid!
    if (allSixCaptured && validation is CubeValidationResult.Valid) {
      viewModelScope.launch(Dispatchers.Default) {
        val solved = CubeSolver.solve(correctedCube)
        _uiState.update { current ->
          if (current.cubeState == correctedCube) {
            current.copy(solveResult = solved, currentStep = 0, stepDirection = StepDirection.NONE)
          } else {
            current
          }
        }
      }
    }
  }

  private fun storeFaceWarmScores(face: CubeFace, faceScores: FloatArray) {
    val start = face.ordinal * 9
    for (i in 0 until minOf(9, faceScores.size)) {
      stickerWarmScores[start + i] = faceScores[i]
    }
  }

  /**
   * Simulates scanning all 6 faces with the camera using a realistic scramble
   * (useful on emulators or when testing without a physical cube).
   */
  fun simulateCameraScanOfCurrentFace() {
    val state = _uiState.value
    val face = state.nextUnscannedFace ?: state.currentScanFace
    val simulatedColors = state.cubeState.faceStickers(face)
    isSamplePresetLoaded = false
    rawScannedCubeState = state.cubeState
    _uiState.update {
      it.copy(
        liveDetectedFace = face,
        liveReticleColors = simulatedColors,
        stabilityProgress = 1f,
      )
    }
    captureFaceInternal(face = face, overrideColors = simulatedColors)
  }

  fun startFreshSixFaceScan(enableScanning: Boolean = false) {
    consecutiveStableFrames = 0
    recentFrameWindow.clear()
    manualReticleOverrides.clear()
    warmFaceCaptures.clear()
    stickerWarmScores.fill(Float.NaN)
    rawScannedCubeState = CubeState.solved()
    isSamplePresetLoaded = false
    lastAutoSnappedFace = null
    CubeColorDetector.clearCalibration()
    val firstFace = CubeFace.GUIDED_SCAN_ORDER[0]
    val (yaw, pitch) = orbitAnglesForFace(firstFace)
    _uiState.update {
      it.copy(
        scanOrderIndex = 0,
        capturedFaces = emptySet(),
        liveDetectedFace = null,
        liveReticleColors = null,
        isCubeFaceVisibleInCamera = false,
        stabilityProgress = 0f,
        autoSnapEnabled = enableScanning,
        lastAutocorrectMessage = null,
        totalAutocorrectedStickers = 0,
        selectedBrushColor = firstFace.defaultColor,
        cameraYaw = yaw,
        cameraPitch = pitch,
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
    rawScannedCubeState = updatedCube
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
    isSamplePresetLoaded = true
    rawScannedCubeState = scrambled
    warmFaceCaptures.clear()
    stickerWarmScores.fill(Float.NaN)
    val validation = CubeSolver.validate(scrambled)
    _uiState.update {
      it.copy(
        cubeState = scrambled,
        activeScrambleTitle = preset.title,
        capturedFaces = CubeFace.entries.toSet(),
        lastAutocorrectMessage = null,
        totalAutocorrectedStickers = 0,
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
    isSamplePresetLoaded = true
    rawScannedCubeState = scrambled
    warmFaceCaptures.clear()
    stickerWarmScores.fill(Float.NaN)
    val validation = CubeSolver.validate(scrambled)
    _uiState.update {
      it.copy(
        cubeState = scrambled,
        activeScrambleTitle = "Random 20-Move Scramble",
        capturedFaces = CubeFace.entries.toSet(),
        lastAutocorrectMessage = null,
        totalAutocorrectedStickers = 0,
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
    isSamplePresetLoaded = true
    rawScannedCubeState = solved
    warmFaceCaptures.clear()
    stickerWarmScores.fill(Float.NaN)
    _uiState.update {
      it.copy(
        cubeState = solved,
        activeScrambleTitle = "Solved Cube",
        capturedFaces = CubeFace.entries.toSet(),
        lastAutocorrectMessage = null,
        totalAutocorrectedStickers = 0,
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
          cameraYaw = if (navigateToSolver) -34f else it.cameraYaw,
          cameraPitch = if (navigateToSolver) 25f else it.cameraPitch,
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
    val (targetYaw, targetPitch) = orbitAnglesForFace(face)
    _uiState.update { it.copy(cameraYaw = targetYaw, cameraPitch = targetPitch) }
  }

  private fun orbitAnglesForFace(face: CubeFace): Pair<Float, Float> = when (face) {
    CubeFace.F -> -22f to 20f
    CubeFace.R -> -68f to 20f
    CubeFace.B -> 158f to 20f
    CubeFace.L -> 68f to 20f
    CubeFace.U -> -25f to 55f
    CubeFace.D -> -25f to -55f
  }
}
