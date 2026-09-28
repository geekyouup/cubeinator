package com.example.cubeinator.ui.main

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.NavigateBefore
import androidx.compose.material.icons.automirrored.filled.NavigateNext
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Casino
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.FastForward
import androidx.compose.material.icons.filled.FastRewind
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.ViewInAr
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation3.runtime.NavKey
import com.example.cubeinator.cube.CubeColor
import com.example.cubeinator.cube.CubeFace
import com.example.cubeinator.cube.CubeMove
import com.example.cubeinator.cube.CubeState
import com.example.cubeinator.cube.CubeValidationResult
import com.example.cubeinator.cube.SampleScramble
import com.example.cubeinator.theme.CubeinatorTheme
import com.example.cubeinator.ui.components.CameraScannerSection
import com.example.cubeinator.ui.components.Cube2DNetView
import com.example.cubeinator.ui.components.Cube3DCanvas
import com.example.cubeinator.ui.components.MiniFaceGrid
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

@Composable
fun MainScreen(
  onItemClick: (NavKey) -> Unit = {},
  modifier: Modifier = Modifier,
  viewModel: MainScreenViewModel = viewModel { MainScreenViewModel() },
) {
  val state by viewModel.uiState.collectAsStateWithLifecycle()
  CubeinatorContent(
    state = state,
    onSelectTab = viewModel::selectTab,
    onSelectScanFace = viewModel::selectScanFace,
    onToggleAutoSnap = viewModel::toggleAutoSnap,
    onAdjustRedOrangeBias = viewModel::adjustRedOrangeBias,
    onReticleCellTap = viewModel::onReticleCellTap,
    onCameraFrameAnalyzed = viewModel::onCameraFrameAnalyzed,
    onCaptureCurrentFace = viewModel::captureCurrentFace,
    onSimulateScanFace = viewModel::simulateCameraScanOfCurrentFace,
    onStartFreshScan = viewModel::startFreshSixFaceScan,
    onSelectBrushColor = viewModel::selectBrushColor,
    onPaintSticker = viewModel::paintSticker,
    onLoadPreset = viewModel::loadSampleScramble,
    onRandomScramble = viewModel::generateRandomScramble,
    onResetSolved = viewModel::resetToSolvedCube,
    onSolveCube = { viewModel.solveCurrentCube(navigateToSolver = true) },
    onStepForward = viewModel::stepForward,
    onStepBackward = viewModel::stepBackward,
    onSeekToStep = viewModel::seekToStep,
    onTogglePlayPause = viewModel::togglePlayPause,
    onSetPlaybackSpeed = viewModel::setPlaybackSpeed,
    onSetVisualizerMode = viewModel::setVisualizerMode,
    onOrbitChange = viewModel::updateCameraOrbit,
    onResetOrbit = viewModel::resetCameraOrbit,
    onFocusFace = viewModel::focusCameraOnFace,
    modifier = modifier,
  )
}

@Composable
fun CubeinatorContent(
  state: CubeinatorUiState,
  onSelectTab: (AppTab) -> Unit,
  onSelectScanFace: (CubeFace) -> Unit,
  onToggleAutoSnap: () -> Unit,
  onAdjustRedOrangeBias: (Float) -> Unit = {},
  onReticleCellTap: (Int) -> Unit = {},
  onCameraFrameAnalyzed: (com.example.cubeinator.camera.ReticleDetectionResult) -> Unit,
  onCaptureCurrentFace: () -> Unit,
  onSimulateScanFace: () -> Unit,
  onStartFreshScan: () -> Unit,
  onSelectBrushColor: (CubeColor) -> Unit,
  onPaintSticker: (CubeFace, Int) -> Unit,
  onLoadPreset: (SampleScramble, Boolean) -> Unit,
  onRandomScramble: (Boolean) -> Unit,
  onResetSolved: () -> Unit,
  onSolveCube: () -> Unit,
  onStepForward: () -> Unit,
  onStepBackward: () -> Unit,
  onSeekToStep: (Int) -> Unit,
  onTogglePlayPause: () -> Unit,
  onSetPlaybackSpeed: (Float) -> Unit,
  onSetVisualizerMode: (VisualizerMode) -> Unit,
  onOrbitChange: (Float, Float) -> Unit,
  onResetOrbit: () -> Unit,
  onFocusFace: (CubeFace) -> Unit,
  modifier: Modifier = Modifier,
) {
  val view = androidx.compose.ui.platform.LocalView.current
  val keepAwake = state.activeTab == AppTab.SCANNER || state.activeTab == AppTab.SOLVER || state.autoSnapEnabled || state.isSolving || state.isPlaying
  androidx.compose.runtime.DisposableEffect(view, keepAwake) {
    view.keepScreenOn = keepAwake
    onDispose {
      view.keepScreenOn = false
    }
  }

  Box(
    modifier = modifier
      .fillMaxSize()
      .background(
        brush = Brush.verticalGradient(
          colors = listOf(
            Color(0xFF10B981),
            Color(0xFF059669),
            Color(0xFF047857),
          )
        )
      ),
  ) {
    // Playful, subtle Rubik's Cube tile pattern in the background
    RubiksCubePatternBackdrop(modifier = Modifier.fillMaxSize())

    Column(
      modifier = Modifier
        .fillMaxSize()
        .padding(horizontal = 14.dp, vertical = 8.dp),
      verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
      // App Header + Mode Switcher
      TopHeaderBar(
        activeTab = state.activeTab,
        totalMoves = state.solveResult?.totalSteps,
        onSelectTab = onSelectTab,
      )

      when (state.activeTab) {
        AppTab.SCANNER -> {
          ScannerAndEditorTab(
            state = state,
            onSelectScanFace = onSelectScanFace,
            onToggleAutoSnap = onToggleAutoSnap,
            onAdjustRedOrangeBias = onAdjustRedOrangeBias,
            onReticleCellTap = onReticleCellTap,
            onCameraFrameAnalyzed = onCameraFrameAnalyzed,
            onCaptureCurrentFace = onCaptureCurrentFace,
            onSimulateScanFace = onSimulateScanFace,
            onStartFreshScan = onStartFreshScan,
            onSelectBrushColor = onSelectBrushColor,
            onPaintSticker = onPaintSticker,
            onLoadPreset = onLoadPreset,
            onRandomScramble = onRandomScramble,
            onResetSolved = onResetSolved,
            onSolveCube = onSolveCube,
            onOrbitChange = onOrbitChange,
            modifier = Modifier.weight(1f),
          )
        }
        AppTab.SOLVER -> {
          SolverWalkthroughTab(
            state = state,
            onStepForward = onStepForward,
            onStepBackward = onStepBackward,
            onSeekToStep = onSeekToStep,
            onTogglePlayPause = onTogglePlayPause,
            onSetPlaybackSpeed = onSetPlaybackSpeed,
            onSetVisualizerMode = onSetVisualizerMode,
            onOrbitChange = onOrbitChange,
            onResetOrbit = onResetOrbit,
            onFocusFace = onFocusFace,
            onSwitchToScanner = { onSelectTab(AppTab.SCANNER) },
            onRandomScramble = { onRandomScramble(true) },
            modifier = Modifier.weight(1f),
          )
        }
      }
    }
  }
}

@Composable
private fun RubiksCubePatternBackdrop(modifier: Modifier = Modifier) {
  val cubePalette = listOf(
    Color(0xFFFFFFFF).copy(alpha = 0.10f),
    Color(0xFFFACC15).copy(alpha = 0.12f),
    Color(0xFF38BDF8).copy(alpha = 0.11f),
    Color(0xFFFB923C).copy(alpha = 0.11f),
    Color(0xFFA7F3D0).copy(alpha = 0.13f),
    Color(0xFFF87171).copy(alpha = 0.10f),
  )
  Canvas(modifier = modifier) {
    val tile = 26.dp.toPx()
    val gap = 5.dp.toPx()
    val corner = CornerRadius(6.dp.toPx(), 6.dp.toPx())

    // Top-left playful tilted 3x3 Rubik's mini-grid
    rotate(degrees = -14f, pivot = Offset(size.width * 0.12f, size.height * 0.08f)) {
      val originX = size.width * 0.02f
      val originY = size.height * 0.02f
      for (r in 0..2) {
        for (c in 0..2) {
          val color = cubePalette[(r * 3 + c) % cubePalette.size]
          drawRoundRect(
            color = color,
            topLeft = Offset(originX + c * (tile + gap), originY + r * (tile + gap)),
            size = Size(tile, tile),
            cornerRadius = corner,
          )
        }
      }
    }

    // Top-right playful tilted 3x3 Rubik's mini-grid
    rotate(degrees = 16f, pivot = Offset(size.width * 0.88f, size.height * 0.10f)) {
      val originX = size.width * 0.76f
      val originY = size.height * 0.03f
      for (r in 0..2) {
        for (c in 0..2) {
          val color = cubePalette[(r * 2 + c + 2) % cubePalette.size]
          drawRoundRect(
            color = color,
            topLeft = Offset(originX + c * (tile + gap), originY + r * (tile + gap)),
            size = Size(tile, tile),
            cornerRadius = corner,
          )
        }
      }
    }

    // Bottom-right playful tilted 3x3 Rubik's mini-grid
    rotate(degrees = -12f, pivot = Offset(size.width * 0.85f, size.height * 0.88f)) {
      val originX = size.width * 0.74f
      val originY = size.height * 0.82f
      for (r in 0..2) {
        for (c in 0..2) {
          val color = cubePalette[(r + c * 2 + 1) % cubePalette.size]
          drawRoundRect(
            color = color,
            topLeft = Offset(originX + c * (tile + gap), originY + r * (tile + gap)),
            size = Size(tile, tile),
            cornerRadius = corner,
          )
        }
      }
    }
  }
}

@Composable
private fun TopHeaderBar(
  activeTab: AppTab,
  totalMoves: Int?,
  onSelectTab: (AppTab) -> Unit,
) {
  Column(
    modifier = Modifier.fillMaxWidth(),
    horizontalAlignment = Alignment.CenterHorizontally,
    verticalArrangement = Arrangement.spacedBy(8.dp),
  ) {
    Text(
      text = "Cubeinator",
      style = MaterialTheme.typography.titleLarge,
      fontWeight = FontWeight.ExtraBold,
      letterSpacing = 0.8.sp,
      color = Color.White,
      textAlign = TextAlign.Center,
      modifier = Modifier
        .fillMaxWidth()
        .padding(top = 2.dp),
    )

    // Two-mode segmented tab bar
    Row(
      modifier = Modifier
        .fillMaxWidth()
        .clip(RoundedCornerShape(16.dp))
        .background(Color(0xFF064E3B).copy(alpha = 0.30f))
        .border(1.dp, Color(0xFFA7F3D0).copy(alpha = 0.45f), RoundedCornerShape(16.dp))
        .padding(4.dp),
      horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
      TabSegmentButton(
        selected = activeTab == AppTab.SCANNER,
        icon = Icons.Default.CameraAlt,
        title = "Scan Cube",
        onClick = { onSelectTab(AppTab.SCANNER) },
        modifier = Modifier.weight(1f),
      )
      TabSegmentButton(
        selected = activeTab == AppTab.SOLVER,
        icon = Icons.Default.ViewInAr,
        title = "Solve Cube",
        onClick = { onSelectTab(AppTab.SOLVER) },
        modifier = Modifier.weight(1f),
      )
    }
  }
}

@Composable
private fun TabSegmentButton(
  selected: Boolean,
  icon: androidx.compose.ui.graphics.vector.ImageVector,
  title: String,
  onClick: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val bg = if (selected) Color.White else Color.Transparent
  val contentColor = if (selected) Color(0xFF047857) else Color.White
  Row(
    modifier = modifier
      .clip(RoundedCornerShape(12.dp))
      .background(bg)
      .clickable(onClick = onClick)
      .padding(vertical = 9.dp, horizontal = 8.dp),
    horizontalArrangement = Arrangement.Center,
    verticalAlignment = Alignment.CenterVertically,
  ) {
    Icon(imageVector = icon, contentDescription = null, tint = contentColor, modifier = Modifier.size(16.dp))
    Spacer(modifier = Modifier.width(6.dp))
    Text(
      text = title,
      color = contentColor,
      fontSize = 12.sp,
      fontWeight = if (selected) FontWeight.ExtraBold else FontWeight.SemiBold,
    )
  }
}

@Composable
private fun ScannerAndEditorTab(
  state: CubeinatorUiState,
  onSelectScanFace: (CubeFace) -> Unit,
  onToggleAutoSnap: () -> Unit,
  onAdjustRedOrangeBias: (Float) -> Unit,
  onReticleCellTap: (Int) -> Unit,
  onCameraFrameAnalyzed: (com.example.cubeinator.camera.ReticleDetectionResult) -> Unit,
  onCaptureCurrentFace: () -> Unit,
  onSimulateScanFace: () -> Unit,
  onStartFreshScan: () -> Unit,
  onSelectBrushColor: (CubeColor) -> Unit,
  onPaintSticker: (CubeFace, Int) -> Unit,
  onLoadPreset: (SampleScramble, Boolean) -> Unit,
  onRandomScramble: (Boolean) -> Unit,
  onResetSolved: () -> Unit,
  onSolveCube: () -> Unit,
  onOrbitChange: (Float, Float) -> Unit,
  modifier: Modifier = Modifier,
) {
  val highlightedFace = state.liveDetectedFace ?: state.currentScanFace

  Column(
    modifier = modifier
      .fillMaxSize()
      .verticalScroll(rememberScrollState()),
    verticalArrangement = Arrangement.spacedBy(10.dp),
  ) {
    // 1. CameraX Continuous Auto-Detection Card
    CameraScannerSection(
      targetFace = state.currentScanFace,
      liveDetectedFace = state.liveDetectedFace,
      capturedFaces = state.capturedFaces,
      liveReticleColors = state.liveReticleColors,
      currentFaceSavedColors = state.cubeState.faceStickers(highlightedFace),
      isCubeFaceVisibleInCamera = state.isCubeFaceVisibleInCamera,
      stabilityProgress = state.stabilityProgress,
      autoSnapEnabled = state.autoSnapEnabled,
      lastAutocorrectMessage = state.lastAutocorrectMessage,
      redOrangeBiasDegrees = state.redOrangeBiasDegrees,
      onSelectFace = onSelectScanFace,
      onToggleAutoSnap = onToggleAutoSnap,
      onStartFreshScan = onStartFreshScan,
      onAdjustRedOrangeBias = onAdjustRedOrangeBias,
      onReticleCellTap = onReticleCellTap,
      onFrameAnalyzed = onCameraFrameAnalyzed,
      onCaptureFace = onCaptureCurrentFace,
      onSimulateScanFace = onSimulateScanFace,
    )

    // 2. Live 3D Cube Model + 2D Flattened Net (fills in automatically as faces are detected!)
    LiveDetectedCubeAndNetCard(
      cubeState = state.cubeState,
      capturedFaces = state.capturedFaces,
      selectedFace = highlightedFace,
      selectedBrushColor = state.selectedBrushColor,
      cameraYaw = state.cameraYaw,
      cameraPitch = state.cameraPitch,
      validationResult = state.validationResult,
      isSolving = state.isSolving,
      onSelectFace = onSelectScanFace,
      onSelectBrushColor = onSelectBrushColor,
      onPaintSticker = onPaintSticker,
      onOrbitChange = onOrbitChange,
      onSolveClick = onSolveCube,
    )

    Spacer(modifier = Modifier.height(6.dp))
  }
}

@Composable
private fun LiveDetectedCubeAndNetCard(
  cubeState: CubeState,
  capturedFaces: Set<CubeFace>,
  selectedFace: CubeFace,
  selectedBrushColor: CubeColor,
  cameraYaw: Float,
  cameraPitch: Float,
  validationResult: CubeValidationResult,
  isSolving: Boolean,
  onSelectFace: (CubeFace) -> Unit,
  onSelectBrushColor: (CubeColor) -> Unit,
  onPaintSticker: (CubeFace, Int) -> Unit,
  onOrbitChange: (Float, Float) -> Unit,
  onSolveClick: () -> Unit,
) {
  val isComplete = capturedFaces.size == 6
  val isValid = isComplete && validationResult is CubeValidationResult.Valid

  Card(
    modifier = Modifier
      .fillMaxWidth()
      .border(1.5.dp, Color(0xFFA7F3D0), RoundedCornerShape(22.dp)),
    shape = RoundedCornerShape(22.dp),
    colors = CardDefaults.cardColors(containerColor = Color(0xFFF0FDF4)),
    elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
  ) {
    Column(
      modifier = Modifier.padding(12.dp),
      verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Text(
          text = "Live 3D Model & 2D Flattened Layout",
          style = MaterialTheme.typography.titleSmall,
          fontWeight = FontWeight.ExtraBold,
          color = Color(0xFF064E3B),
        )
        Text(
          text = "${capturedFaces.size}/6 filled",
          color = if (isComplete) Color(0xFF059669) else Color(0xFF0284C7),
          fontSize = 11.sp,
          fontWeight = FontWeight.ExtraBold,
        )
      }

      // Side-by-side: Live 3D Cube (left) + Live 2D Flattened Net (right)
      Row(
        modifier = Modifier
          .fillMaxWidth()
          .height(165.dp)
          .clip(RoundedCornerShape(16.dp))
          .background(Color(0xFFDCFCE7))
          .border(1.dp, Color(0xFF86EFAC), RoundedCornerShape(16.dp))
          .padding(6.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
      ) {
        // 3D Cube Model (auto-orbits and fills in colors as faces are detected)
        Box(
          modifier = Modifier
            .weight(0.95f)
            .fillMaxHeight()
            .clipToBounds(),
          contentAlignment = Alignment.Center,
        ) {
          Cube3DCanvas(
            cubeState = cubeState,
            highlightedFace = selectedFace,
            capturedFaces = capturedFaces,
            yawDegrees = cameraYaw,
            pitchDegrees = cameraPitch,
            onOrbitChange = onOrbitChange,
            modifier = Modifier.fillMaxSize(),
          )
        }

        // 2D Flattened Layout (auto-fills each face and allows tap-to-edit)
        BoxWithConstraints(
          modifier = Modifier
            .weight(1.05f)
            .fillMaxHeight(),
          contentAlignment = Alignment.Center,
        ) {
          val cellW = (maxWidth - 42.dp) / 12f
          val cellH = (maxHeight - 30.dp) / 9f
          val dynamicCellSize = minOf(cellW, cellH).coerceIn(8.dp, 15.dp)
          Cube2DNetView(
            cubeState = cubeState,
            highlightedFace = selectedFace,
            capturedFaces = capturedFaces,
            cellSize = dynamicCellSize,
            onStickerClick = { face, idx ->
              onSelectFace(face)
              onPaintSticker(face, idx)
            },
          )
        }
      }

      // Compact 6-Color Touch-Up Palette Bar (tap a color, then tap any sticker on the 2D net)
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(5.dp),
      ) {
        for (color in CubeColor.entries) {
          val count = cubeState.stickers.count { it == color }
          val isSelected = color == selectedBrushColor
          val countOk = count == 9
          Row(
            modifier = Modifier
              .weight(1f)
              .clip(RoundedCornerShape(10.dp))
              .background(if (isSelected) Color(0xFFE0F2FE) else Color.White)
              .border(
                width = if (isSelected) 2.dp else 1.dp,
                color = if (isSelected) Color(0xFF0284C7) else Color(0xFF86EFAC),
                shape = RoundedCornerShape(10.dp),
              )
              .clickable { onSelectBrushColor(color) }
              .padding(vertical = 5.dp, horizontal = 4.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
          ) {
            Box(
              modifier = Modifier
                .size(12.dp)
                .clip(CircleShape)
                .background(color.composeColor)
                .border(0.75.dp, color.borderColor, CircleShape),
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(
              text = "$count/9",
              color = if (countOk) Color(0xFF059669) else Color(0xFFE11D48),
              fontSize = 10.sp,
              fontWeight = FontWeight.ExtraBold,
            )
          }
        }
      }

      if (isComplete && validationResult is CubeValidationResult.Invalid) {
        Text(
          text = "${validationResult.reason}: ${validationResult.details}",
          color = Color(0xFFE11D48),
          fontSize = 11.sp,
          fontWeight = FontWeight.SemiBold,
        )
      }

      if (isComplete) {
        Button(
          onClick = onSolveClick,
          enabled = isValid && !isSolving,
          modifier = Modifier.fillMaxWidth(),
          shape = RoundedCornerShape(14.dp),
          colors = ButtonDefaults.buttonColors(
            containerColor = Color(0xFF059669),
            contentColor = Color.White,
            disabledContainerColor = Color(0xFFA7F3D0),
            disabledContentColor = Color(0xFF064E3B),
          ),
        ) {
          if (isSolving) {
            CircularProgressIndicator(
              modifier = Modifier.size(18.dp),
              strokeWidth = 2.dp,
              color = Color.White,
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text("Computing Optimal Solution...", fontWeight = FontWeight.Bold, color = Color.White)
          } else {
            Icon(Icons.Default.ViewInAr, contentDescription = null, modifier = Modifier.size(18.dp), tint = Color.White)
            Spacer(modifier = Modifier.width(8.dp))
            Text("Solve Cube →", fontWeight = FontWeight.ExtraBold, color = Color.White)
          }
        }
      }
    }
  }
}

@Composable
private fun SolverWalkthroughTab(
  state: CubeinatorUiState,
  onStepForward: () -> Unit,
  onStepBackward: () -> Unit,
  onSeekToStep: (Int) -> Unit,
  onTogglePlayPause: () -> Unit,
  onSetPlaybackSpeed: (Float) -> Unit,
  onSetVisualizerMode: (VisualizerMode) -> Unit,
  onOrbitChange: (Float, Float) -> Unit,
  onResetOrbit: () -> Unit,
  onFocusFace: (CubeFace) -> Unit,
  onSwitchToScanner: () -> Unit,
  onRandomScramble: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val solveResult = state.solveResult
  if (solveResult == null) {
    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
      Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
      ) {
        CircularProgressIndicator(color = Color(0xFF38BDF8))
        Text("Preparing optimal solution...", color = Color.White)
      }
    }
    return
  }

  // Smooth 0f..1f animation for both FORWARD and BACKWARD single-step transitions (0.5x speed default = 1200ms)
  val animProgress = remember { Animatable(1f) }
  val durationMs = (600f / state.playbackSpeed).roundToInt().coerceIn(240, 1600)

  LaunchedEffect(state.animationTriggerId) {
    if (state.animationTriggerId > 0L && state.stepDirection != StepDirection.NONE) {
      animProgress.snapTo(0f)
      animProgress.animateTo(
        targetValue = 1f,
        animationSpec = tween(durationMillis = durationMs, easing = FastOutSlowInEasing),
      )
    } else {
      animProgress.snapTo(1f)
    }
  }

  // Auto-play loop when Play is active (at 0.5x speed, pauses 1.6s showing the next move before turning)
  LaunchedEffect(state.isPlaying, state.currentStep, state.playbackSpeed) {
    if (state.isPlaying) {
      if (state.currentStep < state.totalSteps) {
        val pauseBetweenStepsMs = (800f / state.playbackSpeed).roundToInt().coerceIn(300, 2000)
        val waitMs = if (state.stepDirection == StepDirection.NONE) {
          pauseBetweenStepsMs.toLong()
        } else {
          (durationMs + pauseBetweenStepsMs).toLong()
        }
        delay(waitMs)
        onStepForward()
      } else {
        onTogglePlayPause()
      }
    }
  }

  // Determine the base state and animated move for the 3D renderer:
  // - When stepping FORWARD from (step - 1) -> step:
  //   Start from statesAtEachStep[step - 1] and animate moves[step - 1] from 0f..1f.
  // - When stepping BACKWARD from (step + 1) -> step:
  //   Start from statesAtEachStep[step + 1] and animate moves[step].inverse() from 0f..1f!
  val isAnimating = animProgress.value < 0.999f && state.stepDirection != StepDirection.NONE
  val renderBaseState: CubeState
  val renderActiveMove: CubeMove?
  val renderMoveProgress: Float

  if (isAnimating && state.stepDirection == StepDirection.FORWARD && state.currentStep > 0) {
    renderBaseState = solveResult.statesAtEachStep[state.currentStep - 1]
    renderActiveMove = solveResult.moves[state.currentStep - 1]
    renderMoveProgress = animProgress.value
  } else if (isAnimating && state.stepDirection == StepDirection.BACKWARD && state.currentStep < state.totalSteps) {
    renderBaseState = solveResult.statesAtEachStep[state.currentStep + 1]
    renderActiveMove = solveResult.moves[state.currentStep].inverse()
    renderMoveProgress = animProgress.value
  } else {
    renderBaseState = state.displayedCubeState
    // Show directional turn preview arrow for the upcoming move (before doing it)
    renderActiveMove = if (state.currentStep < state.totalSteps) {
      solveResult.moves[state.currentStep]
    } else {
      null
    }
    renderMoveProgress = 0f
  }

  Column(
    modifier = modifier.fillMaxSize(),
    verticalArrangement = Arrangement.spacedBy(8.dp),
  ) {
    // 1. Main 3D & 2D Cube Preview Card (always stays the exact same size across all steps)
    Card(
      modifier = Modifier
        .fillMaxWidth()
        .weight(1f)
        .border(1.5.dp, Color(0xFFA7F3D0), RoundedCornerShape(22.dp)),
      shape = RoundedCornerShape(22.dp),
      colors = CardDefaults.cardColors(containerColor = Color(0xFFF0FDF4)),
      elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
    ) {
      Column(
        modifier = Modifier
          .fillMaxSize()
          .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
      ) {
        // Fixed-height header row with Step progress + Reset View button
        Row(
          modifier = Modifier
            .fillMaxWidth()
            .height(28.dp),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically,
        ) {
          Text(
            text = when {
              state.totalSteps == 0 -> "Already Solved"
              state.currentStep == 0 -> "Ready to Solve (${state.totalSteps} Moves)"
              state.currentStep == state.totalSteps -> "Cube Solved! (${state.totalSteps}/${state.totalSteps})"
              else -> "Step ${state.currentStep} of ${state.totalSteps}"
            },
            color = if (state.currentStep == state.totalSteps) Color(0xFF059669) else Color(0xFF064E3B),
            fontSize = 13.sp,
            fontWeight = FontWeight.ExtraBold,
            maxLines = 1,
          )

          Box(
            modifier = Modifier
              .clip(RoundedCornerShape(8.dp))
              .background(Color(0xFFDCFCE7))
              .border(1.dp, Color(0xFF86EFAC), RoundedCornerShape(8.dp))
              .clickable { onResetOrbit() }
              .padding(horizontal = 10.dp, vertical = 4.dp),
          ) {
            Text(
              text = "Reset View",
              color = Color(0xFF047857),
              fontSize = 11.sp,
              fontWeight = FontWeight.Bold,
              maxLines = 1,
            )
          }
        }

        // Fixed-proportion stage showing both the 3D Cube and 2D Net (unobstructed & constant size)
        BoxWithConstraints(
          modifier = Modifier
            .weight(1f)
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(Color(0xFFDCFCE7))
            .border(1.dp, Color(0xFF86EFAC), RoundedCornerShape(16.dp))
            .padding(6.dp)
            .clipToBounds(),
          contentAlignment = Alignment.Center,
        ) {
          if (maxWidth >= maxHeight * 1.05f) {
            Row(
              modifier = Modifier.fillMaxSize(),
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
              Box(
                modifier = Modifier
                  .weight(0.95f)
                  .fillMaxHeight()
                  .clipToBounds(),
                contentAlignment = Alignment.Center,
              ) {
                Cube3DCanvas(
                  cubeState = renderBaseState,
                  activeMove = renderActiveMove,
                  moveProgress = renderMoveProgress,
                  highlightedFace = renderActiveMove?.face,
                  yawDegrees = state.cameraYaw,
                  pitchDegrees = state.cameraPitch,
                  onOrbitChange = onOrbitChange,
                  modifier = Modifier.fillMaxSize(),
                )
                if (renderActiveMove != null) {
                  NextMoveCubeOverlayBadge(
                    move = renderActiveMove,
                    modifier = Modifier
                      .align(Alignment.TopStart)
                      .padding(4.dp),
                  )
                }
              }
              BoxWithConstraints(
                modifier = Modifier
                  .weight(1.05f)
                  .fillMaxHeight(),
                contentAlignment = Alignment.Center,
              ) {
                val splitCellW = (maxWidth - 42.dp) / 12f
                val splitCellH = (maxHeight - 32.dp) / 9f
                val splitCellSize = minOf(splitCellW, splitCellH).coerceIn(8.dp, 16.dp)
                Cube2DNetView(
                  cubeState = state.displayedCubeState,
                  highlightedFace = renderActiveMove?.face,
                  cellSize = splitCellSize,
                )
              }
            }
          } else {
            Column(
              modifier = Modifier.fillMaxSize(),
              horizontalAlignment = Alignment.CenterHorizontally,
              verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
              Box(
                modifier = Modifier
                  .weight(1.15f)
                  .fillMaxWidth()
                  .clipToBounds(),
                contentAlignment = Alignment.Center,
              ) {
                Cube3DCanvas(
                  cubeState = renderBaseState,
                  activeMove = renderActiveMove,
                  moveProgress = renderMoveProgress,
                  highlightedFace = renderActiveMove?.face,
                  yawDegrees = state.cameraYaw,
                  pitchDegrees = state.cameraPitch,
                  onOrbitChange = onOrbitChange,
                  modifier = Modifier.fillMaxSize(),
                )
                if (renderActiveMove != null) {
                  NextMoveCubeOverlayBadge(
                    move = renderActiveMove,
                    modifier = Modifier
                      .align(Alignment.TopStart)
                      .padding(4.dp),
                  )
                }
              }
              BoxWithConstraints(
                modifier = Modifier
                  .weight(0.85f)
                  .fillMaxWidth(),
                contentAlignment = Alignment.Center,
              ) {
                val splitCellW = (maxWidth - 42.dp) / 12f
                val splitCellH = (maxHeight - 32.dp) / 9f
                val splitCellSize = minOf(splitCellW, splitCellH).coerceIn(8.dp, 16.dp)
                Cube2DNetView(
                  cubeState = state.displayedCubeState,
                  highlightedFace = renderActiveMove?.face,
                  cellSize = splitCellSize,
                )
              }
            }
          }
        }
      }
    }

    // 2. Fixed-height Move Sequence Ribbon (tappable pills for every step 0..N)
    MoveSequenceRibbon(
      moves = solveResult.moves,
      currentStep = state.currentStep,
      onSelectStep = onSeekToStep,
    )

    // 3. Fixed-height Next Move Coaching Card (never resizes when text length changes)
    StepInstructionCard(
      state = state,
    )

    // 4. Simplified Playback Controls Card (Slider + Prev / Play / Next)
    BidirectionalTransportCard(
      currentStep = state.currentStep,
      totalSteps = state.totalSteps,
      isPlaying = state.isPlaying,
      onSeekToStep = onSeekToStep,
      onStepBackward = onStepBackward,
      onTogglePlayPause = onTogglePlayPause,
      onStepForward = onStepForward,
    )
  }
}

@Composable
private fun NextMoveCubeOverlayBadge(
  move: CubeMove,
  modifier: Modifier = Modifier,
) {
  val faceColor = move.face.defaultColor
  val turnSymbol = when (move.turns) {
    1 -> "↻"
    -1 -> "↺"
    2 -> "180°"
    else -> "↻"
  }
  val textColor = if (faceColor == CubeColor.WHITE || faceColor == CubeColor.YELLOW) {
    Color(0xFF0F172A)
  } else {
    Color.White
  }

  Row(
    modifier = modifier
      .clip(RoundedCornerShape(12.dp))
      .background(faceColor.composeColor)
      .border(2.dp, Color(0xFF0284C7), RoundedCornerShape(12.dp))
      .padding(horizontal = 10.dp, vertical = 5.dp),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(4.dp),
  ) {
    Text(
      text = move.notation,
      color = textColor,
      fontSize = 16.sp,
      fontWeight = FontWeight.Black,
      maxLines = 1,
    )
    Text(
      text = turnSymbol,
      color = textColor,
      fontSize = 14.sp,
      fontWeight = FontWeight.ExtraBold,
      maxLines = 1,
    )
  }
}

@Composable
private fun MoveSequenceRibbon(
  moves: List<CubeMove>,
  currentStep: Int,
  onSelectStep: (Int) -> Unit,
) {
  val listState = rememberLazyListState()
  LaunchedEffect(currentStep) {
    listState.animateScrollToItem(currentStep.coerceAtLeast(0))
  }

  LazyRow(
    state = listState,
    modifier = Modifier
      .fillMaxWidth()
      .height(34.dp),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(6.dp),
    contentPadding = PaddingValues(horizontal = 2.dp),
  ) {
    item {
      val isAtStart = currentStep == 0
      Box(
        modifier = Modifier
          .clip(RoundedCornerShape(10.dp))
          .background(if (isAtStart) Color.White else Color(0xFF064E3B).copy(alpha = 0.28f))
          .border(
            width = 1.dp,
            color = if (isAtStart) Color.White else Color(0xFFA7F3D0).copy(alpha = 0.45f),
            shape = RoundedCornerShape(10.dp),
          )
          .clickable { onSelectStep(0) }
          .padding(horizontal = 10.dp, vertical = 5.dp),
      ) {
        Text(
          text = "0. Start",
          color = if (isAtStart) Color(0xFF047857) else Color.White,
          fontSize = 12.sp,
          fontWeight = FontWeight.Bold,
          maxLines = 1,
        )
      }
    }

    itemsIndexed(moves) { idx, move ->
      val stepNum = idx + 1
      val isCurrentApplied = currentStep == stepNum
      val isNextUp = currentStep == idx
      val isCompleted = stepNum < currentStep

      val bgColor = when {
        isCurrentApplied -> Color.White
        isNextUp -> Color(0xFF0284C7)
        isCompleted -> Color(0xFF064E3B).copy(alpha = 0.50f)
        else -> Color(0xFF064E3B).copy(alpha = 0.25f)
      }
      val borderColor = when {
        isCurrentApplied -> Color.White
        isNextUp -> Color(0xFF7DD3FC)
        isCompleted -> Color(0xFFA7F3D0).copy(alpha = 0.6f)
        else -> Color(0xFFA7F3D0).copy(alpha = 0.35f)
      }
      val textColor = if (isCurrentApplied) Color(0xFF047857) else Color.White

      Row(
        modifier = Modifier
          .clip(RoundedCornerShape(10.dp))
          .background(bgColor)
          .border(1.dp, borderColor, RoundedCornerShape(10.dp))
          .clickable { onSelectStep(stepNum) }
          .padding(horizontal = 10.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp),
      ) {
        Box(
          modifier = Modifier
            .size(9.dp)
            .clip(CircleShape)
            .background(move.face.defaultColor.composeColor)
            .border(0.75.dp, move.face.defaultColor.borderColor, CircleShape),
        )
        Text(
          text = "$stepNum. ${move.notation}",
          color = textColor,
          fontSize = 12.sp,
          fontWeight = if (isCurrentApplied || isNextUp) FontWeight.ExtraBold else FontWeight.SemiBold,
          maxLines = 1,
        )
      }
    }
  }
}

@Composable
private fun StepInstructionCard(
  state: CubeinatorUiState,
) {
  val moves = state.solveResult?.moves.orEmpty()
  val isSolvedAtEnd = state.currentStep >= state.totalSteps

  // Strict fixed height (76.dp) so the Cube Preview window above never changes size
  Card(
    modifier = Modifier
      .fillMaxWidth()
      .height(76.dp)
      .border(1.5.dp, Color(0xFFA7F3D0), RoundedCornerShape(18.dp)),
    shape = RoundedCornerShape(18.dp),
    colors = CardDefaults.cardColors(
      containerColor = if (isSolvedAtEnd) Color(0xFFDCFCE7) else Color(0xFFF0FDF4),
    ),
    elevation = CardDefaults.cardElevation(defaultElevation = 3.dp),
  ) {
    Row(
      modifier = Modifier
        .fillMaxSize()
        .padding(horizontal = 14.dp),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
      if (isSolvedAtEnd) {
        Icon(
          imageVector = Icons.Default.CheckCircle,
          contentDescription = null,
          tint = Color(0xFF059669),
          modifier = Modifier.size(44.dp),
        )
        Column(
          modifier = Modifier.weight(1f),
          verticalArrangement = Arrangement.Center,
        ) {
          Text(
            text = "Cube Solved in ${state.totalSteps} Moves!",
            color = Color(0xFF064E3B),
            fontWeight = FontWeight.ExtraBold,
            fontSize = 14.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
          )
          Text(
            text = "Use Prev or the slider below to review any move.",
            color = Color(0xFF047857),
            fontSize = 12.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
          )
        }
      } else {
        val nextMove = moves[state.currentStep]

        // Prominent Move Badge (fixed 48.dp square)
        Box(
          modifier = Modifier
            .size(48.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(nextMove.face.defaultColor.composeColor)
            .border(2.dp, nextMove.face.defaultColor.borderColor, RoundedCornerShape(14.dp)),
          contentAlignment = Alignment.Center,
        ) {
          Text(
            text = nextMove.notation,
            fontSize = 19.sp,
            fontWeight = FontWeight.Black,
            color = if (nextMove.face.defaultColor == CubeColor.WHITE ||
              nextMove.face.defaultColor == CubeColor.YELLOW
            ) {
              Color(0xFF0F172A)
            } else {
              Color.White
            },
          )
        }

        Column(
          modifier = Modifier.weight(1f),
          verticalArrangement = Arrangement.Center,
        ) {
          Text(
            text = "Next Move (${state.currentStep + 1}/${state.totalSteps}): ${nextMove.directionLabel}",
            color = Color(0xFF0284C7),
            fontWeight = FontWeight.ExtraBold,
            fontSize = 12.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
          )
          Text(
            text = nextMove.humanInstruction,
            color = Color(0xFF064E3B),
            fontWeight = FontWeight.Bold,
            fontSize = 12.sp,
            lineHeight = 15.sp,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
          )
        }
      }
    }
  }
}

@Composable
private fun BidirectionalTransportCard(
  currentStep: Int,
  totalSteps: Int,
  isPlaying: Boolean,
  onSeekToStep: (Int) -> Unit,
  onStepBackward: () -> Unit,
  onTogglePlayPause: () -> Unit,
  onStepForward: () -> Unit,
) {
  Card(
    modifier = Modifier
      .fillMaxWidth()
      .border(1.5.dp, Color(0xFFA7F3D0), RoundedCornerShape(18.dp)),
    shape = RoundedCornerShape(18.dp),
    colors = CardDefaults.cardColors(containerColor = Color(0xFFF0FDF4)),
    elevation = CardDefaults.cardElevation(defaultElevation = 3.dp),
  ) {
    Column(
      modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
      verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
      // Interactive Scrubber Slider (0 .. totalSteps)
      if (totalSteps > 0) {
        Row(
          modifier = Modifier
            .fillMaxWidth()
            .height(24.dp),
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
          Text(
            text = "0",
            color = Color(0xFF047857),
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
          )
          Slider(
            value = currentStep.toFloat(),
            onValueChange = { onSeekToStep(it.roundToInt()) },
            valueRange = 0f..totalSteps.toFloat(),
            steps = (totalSteps - 1).coerceAtLeast(0),
            modifier = Modifier
              .weight(1f)
              .height(24.dp),
            colors = SliderDefaults.colors(
              thumbColor = Color(0xFF059669),
              activeTrackColor = Color(0xFF10B981),
              inactiveTrackColor = Color(0xFFA7F3D0),
            ),
          )
          Text(
            text = "$totalSteps",
            color = Color(0xFF059669),
            fontSize = 11.sp,
            fontWeight = FontWeight.ExtraBold,
          )
        }
      }

      // Simplified 3-Button Transport Controls Row: < Prev, Play/Pause, Next >
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
      ) {
        // Step Backward (< Prev)
        Button(
          onClick = onStepBackward,
          enabled = currentStep > 0,
          modifier = Modifier.weight(1f),
          shape = RoundedCornerShape(12.dp),
          contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
          colors = ButtonDefaults.buttonColors(
            containerColor = Color(0xFF047857),
            contentColor = Color.White,
            disabledContainerColor = Color(0xFFA7F3D0),
            disabledContentColor = Color(0xFF064E3B).copy(alpha = 0.45f),
          ),
        ) {
          Icon(
            imageVector = Icons.AutoMirrored.Filled.NavigateBefore,
            contentDescription = "Previous Step",
            modifier = Modifier.size(20.dp),
          )
          Spacer(modifier = Modifier.width(2.dp))
          Text("Prev", fontWeight = FontWeight.Bold, fontSize = 13.sp)
        }

        // Play / Pause Auto-Walkthrough
        Button(
          onClick = onTogglePlayPause,
          enabled = totalSteps > 0,
          modifier = Modifier.weight(1.15f),
          shape = RoundedCornerShape(14.dp),
          contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
          colors = ButtonDefaults.buttonColors(
            containerColor = if (isPlaying) Color(0xFFF59E0B) else Color(0xFF0284C7),
            contentColor = Color.White,
          ),
        ) {
          Icon(
            imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
            contentDescription = if (isPlaying) "Pause" else "Play",
            modifier = Modifier.size(20.dp),
          )
          Spacer(modifier = Modifier.width(4.dp))
          Text(
            text = if (isPlaying) "Pause" else if (currentStep >= totalSteps) "Replay" else "Play",
            fontWeight = FontWeight.ExtraBold,
            fontSize = 13.sp,
          )
        }

        // Step Forward (Next >)
        Button(
          onClick = onStepForward,
          enabled = currentStep < totalSteps,
          modifier = Modifier.weight(1f),
          shape = RoundedCornerShape(12.dp),
          contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
          colors = ButtonDefaults.buttonColors(
            containerColor = Color(0xFF059669),
            contentColor = Color.White,
            disabledContainerColor = Color(0xFFA7F3D0),
            disabledContentColor = Color(0xFF064E3B).copy(alpha = 0.45f),
          ),
        ) {
          Text("Next", fontWeight = FontWeight.Bold, fontSize = 13.sp)
          Spacer(modifier = Modifier.width(2.dp))
          Icon(
            imageVector = Icons.AutoMirrored.Filled.NavigateNext,
            contentDescription = "Next Step",
            modifier = Modifier.size(20.dp),
          )
        }
      }
    }
  }
}

@Preview(showBackground = true, widthDp = 412, heightDp = 892)
@Composable
fun MainScreenPreview() {
  CubeinatorTheme {
    val sampleState = SampleScramble.PRESETS.first().buildState()
    CubeinatorContent(
      state = CubeinatorUiState(
        activeTab = AppTab.SOLVER,
        cubeState = sampleState,
        solveResult = com.example.cubeinator.cube.SolveResult(
          moves = SampleScramble.PRESETS.first().moves.reversed().map { it.inverse() },
          statesAtEachStep = listOf(sampleState, CubeState.solved()),
          solveTimeMs = 8L,
        ),
        currentStep = 0,
      ),
      onSelectTab = {},
      onSelectScanFace = {},
      onToggleAutoSnap = {},
      onCameraFrameAnalyzed = {},
      onCaptureCurrentFace = {},
      onSimulateScanFace = {},
      onStartFreshScan = {},
      onSelectBrushColor = {},
      onPaintSticker = { _, _ -> },
      onLoadPreset = { _, _ -> },
      onRandomScramble = {},
      onResetSolved = {},
      onSolveCube = {},
      onStepForward = {},
      onStepBackward = {},
      onSeekToStep = {},
      onTogglePlayPause = {},
      onSetPlaybackSpeed = {},
      onSetVisualizerMode = {},
      onOrbitChange = { _, _ -> },
      onResetOrbit = {},
      onFocusFace = {},
    )
  }
}
