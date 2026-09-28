package com.example.cubeinator.ui.main

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
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
  Surface(
    modifier = modifier.fillMaxSize(),
    color = Color(0xFF020617),
  ) {
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
private fun TopHeaderBar(
  activeTab: AppTab,
  totalMoves: Int?,
  onSelectTab: (AppTab) -> Unit,
) {
  Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.SpaceBetween,
      verticalAlignment = Alignment.CenterVertically,
    ) {
      Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
      ) {
        // Mini 2x2x2 cube emblem
        Box(
          modifier = Modifier
            .size(36.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(
              Brush.linearGradient(
                listOf(Color(0xFF38BDF8), Color(0xFF2563EB))
              )
            ),
          contentAlignment = Alignment.Center,
        ) {
          Icon(
            imageVector = Icons.Default.ViewInAr,
            contentDescription = null,
            tint = Color.White,
            modifier = Modifier.size(22.dp),
          )
        }
        Column {
          Text(
            text = "Cubeinator",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.ExtraBold,
            color = Color.White,
          )
          Text(
            text = "Camera Scanner & Optimal 3D Solver",
            style = MaterialTheme.typography.labelSmall,
            color = Color(0xFF94A3B8),
          )
        }
      }

      if (totalMoves != null) {
        Box(
          modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(Color(0xFF0F172A))
            .border(1.dp, Color(0xFF1E293B), RoundedCornerShape(50))
            .padding(horizontal = 10.dp, vertical = 4.dp),
        ) {
          Text(
            text = if (totalMoves == 0) "Solved!" else "$totalMoves Moves",
            color = Color(0xFF38BDF8),
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
          )
        }
      }
    }

    // Two-mode segmented tab bar
    Row(
      modifier = Modifier
        .fillMaxWidth()
        .clip(RoundedCornerShape(14.dp))
        .background(Color(0xFF0F172A))
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
  val bg = if (selected) Color(0xFF0284C7) else Color.Transparent
  val contentColor = if (selected) Color.White else Color(0xFF94A3B8)
  Row(
    modifier = modifier
      .clip(RoundedCornerShape(10.dp))
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
      fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
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
  Column(
    modifier = modifier
      .fillMaxSize()
      .verticalScroll(rememberScrollState()),
    verticalArrangement = Arrangement.spacedBy(12.dp),
  ) {
    // CameraX 6-Face Guided Scanner Card right at the top
    CameraScannerSection(
      targetFace = state.currentScanFace,
      capturedFaces = state.capturedFaces,
      liveReticleColors = state.liveReticleColors,
      currentFaceSavedColors = state.cubeState.faceStickers(state.currentScanFace),
      stabilityProgress = state.stabilityProgress,
      autoSnapEnabled = state.autoSnapEnabled,
      redOrangeBiasDegrees = state.redOrangeBiasDegrees,
      onSelectFace = onSelectScanFace,
      onToggleAutoSnap = onToggleAutoSnap,
      onAdjustRedOrangeBias = onAdjustRedOrangeBias,
      onReticleCellTap = onReticleCellTap,
      onFrameAnalyzed = onCameraFrameAnalyzed,
      onCaptureFace = onCaptureCurrentFace,
      onSimulateScanFace = onSimulateScanFace,
    )

    // Primary Solve CTA + Validation Status Card
    ValidationAndSolveBanner(
      validationResult = state.validationResult,
      activeScrambleTitle = state.activeScrambleTitle,
      isSolving = state.isSolving,
      onSolveClick = onSolveCube,
    )

    // Quick Scramble & Test Bar (for immediate testing without needing a physical cube)
    QuickScrambleBar(
      onLoadPreset = onLoadPreset,
      onRandomScramble = onRandomScramble,
      onResetSolved = onResetSolved,
      onStartFreshScan = onStartFreshScan,
    )

    // Manual Touch-Up Palette + Interactive 3x3 Face & 2D Net + 3D Preview
    ManualTouchUpCard(
      cubeState = state.cubeState,
      selectedFace = state.currentScanFace,
      selectedBrushColor = state.selectedBrushColor,
      cameraYaw = state.cameraYaw,
      cameraPitch = state.cameraPitch,
      onSelectFace = onSelectScanFace,
      onSelectBrushColor = onSelectBrushColor,
      onPaintSticker = onPaintSticker,
      onOrbitChange = onOrbitChange,
    )

    Spacer(modifier = Modifier.height(12.dp))
  }
}

@Composable
private fun ValidationAndSolveBanner(
  validationResult: CubeValidationResult,
  activeScrambleTitle: String,
  isSolving: Boolean,
  onSolveClick: () -> Unit,
) {
  val isValid = validationResult is CubeValidationResult.Valid
  Card(
    modifier = Modifier.fillMaxWidth(),
    shape = RoundedCornerShape(18.dp),
    colors = CardDefaults.cardColors(
      containerColor = if (isValid) Color(0xFF0F172A) else Color(0xFF3B0714)
    ),
  ) {
    Column(
      modifier = Modifier.padding(14.dp),
      verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Row(
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(8.dp),
          modifier = Modifier.weight(1f),
        ) {
          Icon(
            imageVector = if (isValid) Icons.Default.CheckCircle else Icons.Default.Warning,
            contentDescription = null,
            tint = if (isValid) Color(0xFF10B981) else Color(0xFFF43F5E),
            modifier = Modifier.size(20.dp),
          )
          Column {
            Text(
              text = if (isValid) "Cube State Valid • $activeScrambleTitle" else (validationResult as CubeValidationResult.Invalid).reason,
              color = Color.White,
              fontWeight = FontWeight.Bold,
              fontSize = 13.sp,
            )
            Text(
              text = if (isValid) {
                "Ready to compute the quickest solution via Kociemba Two-Phase algorithm"
              } else {
                (validationResult as CubeValidationResult.Invalid).details
              },
              color = Color(0xFFCBD5E1),
              fontSize = 11.sp,
            )
          }
        }
      }

      Button(
        onClick = onSolveClick,
        enabled = isValid && !isSolving,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = ButtonDefaults.buttonColors(
          containerColor = Color(0xFF10B981),
          disabledContainerColor = Color(0xFF334155),
        ),
      ) {
        if (isSolving) {
          CircularProgressIndicator(
            modifier = Modifier.size(18.dp),
            strokeWidth = 2.dp,
            color = Color.White,
          )
          Spacer(modifier = Modifier.width(8.dp))
          Text("Computing Optimal Solution...", fontWeight = FontWeight.Bold)
        } else {
          Icon(Icons.Default.ViewInAr, contentDescription = null, modifier = Modifier.size(18.dp))
          Spacer(modifier = Modifier.width(8.dp))
          Text("Show Quickest Step-by-Step Solution", fontWeight = FontWeight.ExtraBold)
        }
      }
    }
  }
}

@Composable
private fun QuickScrambleBar(
  onLoadPreset: (SampleScramble, Boolean) -> Unit,
  onRandomScramble: (Boolean) -> Unit,
  onResetSolved: () -> Unit,
  onStartFreshScan: () -> Unit,
) {
  Card(
    modifier = Modifier.fillMaxWidth(),
    shape = RoundedCornerShape(16.dp),
    colors = CardDefaults.cardColors(containerColor = Color(0xFF0F172A)),
  ) {
    Column(
      modifier = Modifier.padding(12.dp),
      verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Text(
          text = "Sample Cubes & Presets",
          color = Color(0xFFCBD5E1),
          fontSize = 12.sp,
          fontWeight = FontWeight.Bold,
        )
        Text(
          text = "Tap to load & solve",
          color = Color(0xFF64748B),
          fontSize = 11.sp,
        )
      }

      Row(
        modifier = Modifier
          .fillMaxWidth()
          .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
      ) {
        OutlinedButton(
          onClick = { onRandomScramble(true) },
          contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
          colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFF38BDF8)),
        ) {
          Icon(Icons.Default.Casino, contentDescription = null, modifier = Modifier.size(15.dp))
          Spacer(modifier = Modifier.width(5.dp))
          Text("Random 20-Move", fontSize = 12.sp)
        }

        for (preset in SampleScramble.PRESETS) {
          OutlinedButton(
            onClick = { onLoadPreset(preset, true) },
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFE2E8F0)),
          ) {
            Text(preset.title, fontSize = 12.sp)
          }
        }

        OutlinedButton(
          onClick = onStartFreshScan,
          contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
          colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFFACC15)),
        ) {
          Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(15.dp))
          Spacer(modifier = Modifier.width(4.dp))
          Text("Reset 6-Face Scan", fontSize = 12.sp)
        }

        OutlinedButton(
          onClick = onResetSolved,
          contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
          colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFF94A3B8)),
        ) {
          Text("Solved Cube", fontSize = 12.sp)
        }
      }
    }
  }
}

@Composable
private fun ManualTouchUpCard(
  cubeState: CubeState,
  selectedFace: CubeFace,
  selectedBrushColor: CubeColor,
  cameraYaw: Float,
  cameraPitch: Float,
  onSelectFace: (CubeFace) -> Unit,
  onSelectBrushColor: (CubeColor) -> Unit,
  onPaintSticker: (CubeFace, Int) -> Unit,
  onOrbitChange: (Float, Float) -> Unit,
) {
  Card(
    modifier = Modifier.fillMaxWidth(),
    shape = RoundedCornerShape(20.dp),
    colors = CardDefaults.cardColors(containerColor = Color(0xFF0F172A)),
  ) {
    Column(
      modifier = Modifier.padding(14.dp),
      verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
      Text(
        text = "Color Touch-Up & Live Cube Inspector",
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.Bold,
        color = Color.White,
      )
      Text(
        text = "Select a color below, then tap any non-center sticker on the 3x3 face or 2D net to fix misread colors.",
        color = Color(0xFF94A3B8),
        fontSize = 11.sp,
      )

      // 6-Color Palette Bar with live sticker counts (each should be 9)
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
      ) {
        for (color in CubeColor.entries) {
          val count = cubeState.stickers.count { it == color }
          val isSelected = color == selectedBrushColor
          val countOk = count == 9
          Column(
            modifier = Modifier
              .weight(1f)
              .clip(RoundedCornerShape(10.dp))
              .background(if (isSelected) Color(0xFF1E293B) else Color(0xFF020617))
              .border(
                width = if (isSelected) 2.dp else 1.dp,
                color = if (isSelected) Color(0xFF38BDF8) else Color(0xFF334155),
                shape = RoundedCornerShape(10.dp),
              )
              .clickable { onSelectBrushColor(color) }
              .padding(vertical = 6.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(3.dp),
          ) {
            Box(
              modifier = Modifier
                .size(20.dp)
                .clip(CircleShape)
                .background(color.composeColor)
                .border(1.dp, color.borderColor, CircleShape),
            )
            Text(
              text = color.displayName,
              color = Color.White,
              fontSize = 10.sp,
              fontWeight = FontWeight.SemiBold,
            )
            Text(
              text = "$count/9",
              color = if (countOk) Color(0xFF10B981) else Color(0xFFF43F5E),
              fontSize = 10.sp,
              fontWeight = FontWeight.Bold,
            )
          }
        }
      }

      // Side-by-side: Current Face 3x3 Editor + 3D Interactive Preview
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Column(
          horizontalAlignment = Alignment.CenterHorizontally,
          verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
          Text(
            text = "Touch Up ${selectedFace.displayName}",
            color = Color(0xFF38BDF8),
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
          )
          MiniFaceGrid(
            face = selectedFace,
            stickers = cubeState.faceStickers(selectedFace),
            isHighlighted = true,
            cellSize = 38.dp,
            onStickerClick = onPaintSticker,
          )
        }

        Box(
          modifier = Modifier
            .weight(1f)
            .height(155.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(Color(0xFF020617)),
          contentAlignment = Alignment.Center,
        ) {
          Cube3DCanvas(
            cubeState = cubeState,
            highlightedFace = selectedFace,
            yawDegrees = cameraYaw,
            pitchDegrees = cameraPitch,
            onOrbitChange = onOrbitChange,
            modifier = Modifier.fillMaxSize(),
          )
          Text(
            text = "Drag 3D cube to orbit",
            color = Color(0xFF64748B),
            fontSize = 10.sp,
            modifier = Modifier
              .align(Alignment.BottomCenter)
              .padding(bottom = 4.dp),
          )
        }
      }

      // Full 6-Face 2D Net (tapping any face selects & edits it)
      Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
      ) {
        Text(
          text = "Full 6-Face Unfolded Net (Tap any sticker to edit)",
          color = Color(0xFF94A3B8),
          fontSize = 11.sp,
        )
        Cube2DNetView(
          cubeState = cubeState,
          highlightedFace = selectedFace,
          cellSize = 20.dp,
          onStickerClick = { face, idx ->
            onSelectFace(face)
            onPaintSticker(face, idx)
          },
        )
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

  // Smooth 0f..1f animation for both FORWARD and BACKWARD single-step transitions
  val animProgress = remember { Animatable(1f) }
  val durationMs = (600f / state.playbackSpeed).roundToInt().coerceIn(240, 1400)

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

  // Auto-play loop when Play is active (includes ~0.55s pause after each step finishes)
  LaunchedEffect(state.isPlaying, state.currentStep, state.playbackSpeed) {
    if (state.isPlaying) {
      if (state.currentStep < state.totalSteps) {
        val pauseBetweenStepsMs = (550f / state.playbackSpeed).roundToInt().coerceIn(300, 1200)
        val waitMs = (durationMs + pauseBetweenStepsMs).toLong()
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
    // Show directional turn preview arrow for the upcoming move (or the move just completed)
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
    // Compact single-line scrollable Visualizer Mode Bar + Orbit controls (never wraps vertically)
    Row(
      modifier = Modifier
        .fillMaxWidth()
        .horizontalScroll(rememberScrollState()),
      horizontalArrangement = Arrangement.spacedBy(6.dp),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      for (mode in VisualizerMode.entries) {
        val selected = state.visualizerMode == mode
        Box(
          modifier = Modifier
            .clip(RoundedCornerShape(10.dp))
            .background(if (selected) Color(0xFF0284C7) else Color(0xFF0F172A))
            .border(
              width = 1.dp,
              color = if (selected) Color(0xFF38BDF8) else Color(0xFF1E293B),
              shape = RoundedCornerShape(10.dp),
            )
            .clickable { onSetVisualizerMode(mode) }
            .padding(horizontal = 12.dp, vertical = 6.dp),
        ) {
          Text(
            text = mode.label,
            color = if (selected) Color.White else Color(0xFF94A3B8),
            fontSize = 12.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
          )
        }
      }

      if (renderActiveMove != null) {
        Box(
          modifier = Modifier
            .clip(RoundedCornerShape(10.dp))
            .background(Color(0xFF0F172A))
            .border(1.dp, Color(0xFF334155), RoundedCornerShape(10.dp))
            .clickable { onFocusFace(renderActiveMove.face) }
            .padding(horizontal = 10.dp, vertical = 6.dp),
        ) {
          Text(
            text = "Focus ${renderActiveMove.face.symbol} Face",
            color = Color(0xFF38BDF8),
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
          )
        }
      }

      Box(
        modifier = Modifier
          .clip(RoundedCornerShape(10.dp))
          .background(Color(0xFF0F172A))
          .border(1.dp, Color(0xFF334155), RoundedCornerShape(10.dp))
          .clickable { onResetOrbit() }
          .padding(horizontal = 10.dp, vertical = 6.dp),
      ) {
        Text(
          text = "Reset Angle",
          color = Color(0xFFCBD5E1),
          fontSize = 12.sp,
          fontWeight = FontWeight.Medium,
        )
      }
    }

    // Main 3D / 2D Cube Viewport with dedicated non-overlapping header, middle canvas, and footer
    Card(
      modifier = Modifier
        .fillMaxWidth()
        .weight(1f),
      shape = RoundedCornerShape(22.dp),
      colors = CardDefaults.cardColors(containerColor = Color(0xFF0F172A)),
    ) {
      Column(
        modifier = Modifier
          .fillMaxSize()
          .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
      ) {
        // Dedicated top status row (never overlaps cube or net)
        Row(
          modifier = Modifier.fillMaxWidth(),
          verticalAlignment = Alignment.CenterVertically,
        ) {
          Box(
            modifier = Modifier
              .clip(RoundedCornerShape(10.dp))
              .background(Color(0xFF020617).copy(alpha = 0.85f))
              .padding(horizontal = 10.dp, vertical = 4.dp),
          ) {
            Text(
              text = when {
                state.totalSteps == 0 -> "Already Solved"
                state.currentStep == 0 -> "Initial Scrambled State (Step 0/${state.totalSteps})"
                state.currentStep == state.totalSteps -> "Cube Solved! (Step ${state.totalSteps}/${state.totalSteps})"
                else -> "Step ${state.currentStep} of ${state.totalSteps}"
              },
              color = if (state.currentStep == state.totalSteps) Color(0xFF10B981) else Color(0xFF38BDF8),
              fontSize = 12.sp,
              fontWeight = FontWeight.Bold,
            )
          }
        }

        // Dedicated middle area for 3D Cube, 3D + 2D Net, or 2D Net (100% unobstructed)
        BoxWithConstraints(
          modifier = Modifier
            .weight(1f)
            .fillMaxWidth()
            .clipToBounds(),
          contentAlignment = Alignment.Center,
        ) {
          when (state.visualizerMode) {
            VisualizerMode.CUBE_3D_ONLY -> {
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
            }
            VisualizerMode.SPLIT_3D_AND_2D -> {
              if (maxWidth >= maxHeight * 1.05f) {
                // Side-by-side split when viewport is wider than tall
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
                // Top-and-bottom split when viewport is taller than wide
                Column(
                  modifier = Modifier.fillMaxSize(),
                  horizontalAlignment = Alignment.CenterHorizontally,
                  verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                  Box(
                    modifier = Modifier
                      .weight(1.1f)
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
                  }
                  BoxWithConstraints(
                    modifier = Modifier
                      .weight(0.9f)
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
            VisualizerMode.NET_2D_ONLY -> {
              val maxCellW = (maxWidth - 48.dp) / 12f
              val maxCellH = (maxHeight - 36.dp) / 9f
              val netCellSize = minOf(maxCellW, maxCellH).coerceIn(10.dp, 24.dp)
              Cube2DNetView(
                cubeState = state.displayedCubeState,
                highlightedFace = renderActiveMove?.face,
                cellSize = netCellSize,
              )
            }
          }
        }

        // Dedicated bottom hint row (never overlaps cube or net)
        Text(
          text = "Drag 3D cube to inspect any side • Blue arrow shows next turn",
          color = Color(0xFF64748B),
          fontSize = 10.sp,
          modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp),
        )
      }
    }

    // Move Sequence Ribbon (tappable pills for every step 0..N)
    MoveSequenceRibbon(
      moves = solveResult.moves,
      currentStep = state.currentStep,
      onSelectStep = onSeekToStep,
    )

    // Current Step Coaching Card
    StepInstructionCard(
      state = state,
      onRandomScramble = onRandomScramble,
      onSwitchToScanner = onSwitchToScanner,
    )

    // Bidirectional Playback Controls Card (Scrubber + |<, < Prev, Play/Pause, Next >, >| + Speed)
    BidirectionalTransportCard(
      currentStep = state.currentStep,
      totalSteps = state.totalSteps,
      isPlaying = state.isPlaying,
      playbackSpeed = state.playbackSpeed,
      onSeekToStep = onSeekToStep,
      onStepBackward = onStepBackward,
      onTogglePlayPause = onTogglePlayPause,
      onStepForward = onStepForward,
      onSetPlaybackSpeed = onSetPlaybackSpeed,
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
    modifier = Modifier.fillMaxWidth(),
    horizontalArrangement = Arrangement.spacedBy(6.dp),
    contentPadding = PaddingValues(horizontal = 2.dp),
  ) {
    item {
      val isAtStart = currentStep == 0
      Box(
        modifier = Modifier
          .clip(RoundedCornerShape(10.dp))
          .background(if (isAtStart) Color(0xFF0284C7) else Color(0xFF0F172A))
          .border(
            width = 1.dp,
            color = if (isAtStart) Color(0xFF38BDF8) else Color(0xFF1E293B),
            shape = RoundedCornerShape(10.dp),
          )
          .clickable { onSelectStep(0) }
          .padding(horizontal = 10.dp, vertical = 6.dp),
      ) {
        Text(
          text = "0. Start",
          color = if (isAtStart) Color.White else Color(0xFF94A3B8),
          fontSize = 12.sp,
          fontWeight = FontWeight.Bold,
        )
      }
    }

    itemsIndexed(moves) { idx, move ->
      val stepNum = idx + 1
      val isCurrentApplied = currentStep == stepNum
      val isNextUp = currentStep == idx
      val isCompleted = stepNum < currentStep

      val bgColor = when {
        isCurrentApplied -> Color(0xFF0284C7)
        isNextUp -> Color(0xFF1E293B)
        isCompleted -> Color(0xFF064E3B)
        else -> Color(0xFF0F172A)
      }
      val borderColor = when {
        isCurrentApplied -> Color(0xFF38BDF8)
        isNextUp -> Color(0xFF38BDF8).copy(alpha = 0.6f)
        isCompleted -> Color(0xFF10B981).copy(alpha = 0.5f)
        else -> Color(0xFF1E293B)
      }

      Row(
        modifier = Modifier
          .clip(RoundedCornerShape(10.dp))
          .background(bgColor)
          .border(1.dp, borderColor, RoundedCornerShape(10.dp))
          .clickable { onSelectStep(stepNum) }
          .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp),
      ) {
        Box(
          modifier = Modifier
            .size(8.dp)
            .clip(CircleShape)
            .background(move.face.defaultColor.composeColor),
        )
        Text(
          text = "$stepNum. ${move.notation}",
          color = Color.White,
          fontSize = 12.sp,
          fontWeight = if (isCurrentApplied || isNextUp) FontWeight.ExtraBold else FontWeight.Medium,
        )
      }
    }
  }
}

@Composable
private fun StepInstructionCard(
  state: CubeinatorUiState,
  onRandomScramble: () -> Unit,
  onSwitchToScanner: () -> Unit,
) {
  val moves = state.solveResult?.moves.orEmpty()
  val isSolvedAtEnd = state.currentStep >= state.totalSteps

  Card(
    modifier = Modifier.fillMaxWidth(),
    shape = RoundedCornerShape(16.dp),
    colors = CardDefaults.cardColors(
      containerColor = if (isSolvedAtEnd) Color(0xFF064E3B) else Color(0xFF0F172A),
    ),
  ) {
    Row(
      modifier = Modifier
        .fillMaxWidth()
        .padding(horizontal = 14.dp, vertical = 10.dp),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
      if (isSolvedAtEnd) {
        Icon(
          imageVector = Icons.Default.CheckCircle,
          contentDescription = null,
          tint = Color(0xFF10B981),
          modifier = Modifier.size(36.dp),
        )
        Column(modifier = Modifier.weight(1f)) {
          Text(
            text = "Cube Solved in ${state.totalSteps} Moves!",
            color = Color.White,
            fontWeight = FontWeight.ExtraBold,
            fontSize = 14.sp,
          )
          Text(
            text = "Use < Prev or the slider below to step backward and review any move.",
            color = Color(0xFFA7F3D0),
            fontSize = 11.sp,
          )
        }
        OutlinedButton(
          onClick = onRandomScramble,
          contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
          colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
        ) {
          Text("New Scramble", fontSize = 11.sp)
        }
      } else {
        val nextMove = moves[state.currentStep]
        val prevMove = if (state.currentStep > 0) moves[state.currentStep - 1] else null

        // Prominent Move Badge
        Box(
          modifier = Modifier
            .size(50.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(nextMove.face.defaultColor.composeColor)
            .border(2.dp, nextMove.face.defaultColor.borderColor, RoundedCornerShape(14.dp)),
          contentAlignment = Alignment.Center,
        ) {
          Text(
            text = nextMove.notation,
            fontSize = 20.sp,
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

        Column(modifier = Modifier.weight(1f)) {
          Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
          ) {
            Text(
              text = "Next Move (Step ${state.currentStep + 1} of ${state.totalSteps}): ${nextMove.directionLabel}",
              color = Color(0xFF38BDF8),
              fontWeight = FontWeight.Bold,
              fontSize = 12.sp,
            )
          }
          Text(
            text = nextMove.humanInstruction,
            color = Color.White,
            fontWeight = FontWeight.SemiBold,
            fontSize = 13.sp,
          )
          if (state.stepDirection == StepDirection.BACKWARD) {
            val undone = moves[state.currentStep]
            Text(
              text = "↺ Stepped backward: reversed ${undone.notation} (${undone.inverse().notation})",
              color = Color(0xFFFACC15),
              fontSize = 11.sp,
            )
          } else if (prevMove != null) {
            Text(
              text = "Last completed move: Step ${state.currentStep} (${prevMove.notation})",
              color = Color(0xFF94A3B8),
              fontSize = 11.sp,
            )
          }
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
  playbackSpeed: Float,
  onSeekToStep: (Int) -> Unit,
  onStepBackward: () -> Unit,
  onTogglePlayPause: () -> Unit,
  onStepForward: () -> Unit,
  onSetPlaybackSpeed: (Float) -> Unit,
) {
  Card(
    modifier = Modifier.fillMaxWidth(),
    shape = RoundedCornerShape(18.dp),
    colors = CardDefaults.cardColors(containerColor = Color(0xFF0F172A)),
  ) {
    Column(
      modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
      verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
      // Interactive Scrubber Slider (0 .. totalSteps)
      if (totalSteps > 0) {
        Row(
          modifier = Modifier.fillMaxWidth(),
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
          Text(
            text = "0",
            color = Color(0xFF94A3B8),
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
              .height(26.dp),
            colors = SliderDefaults.colors(
              thumbColor = Color(0xFF38BDF8),
              activeTrackColor = Color(0xFF0284C7),
              inactiveTrackColor = Color(0xFF1E293B),
            ),
          )
          Text(
            text = "$totalSteps",
            color = Color(0xFF10B981),
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
          )
        }
      }

      // Transport Controls Row: |<< (Start), < Prev (Step Back), Play/Pause, Next > (Step Forward), >>| (Solved)
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
      ) {
        // Jump to Start
        FilledIconButton(
          onClick = { onSeekToStep(0) },
          enabled = currentStep > 0,
          colors = IconButtonDefaults.filledIconButtonColors(
            containerColor = Color(0xFF1E293B),
            disabledContainerColor = Color(0xFF020617),
          ),
          modifier = Modifier.size(40.dp),
        ) {
          Icon(
            imageVector = Icons.Default.FastRewind,
            contentDescription = "Jump to Start",
            tint = if (currentStep > 0) Color.White else Color(0xFF475569),
          )
        }

        // Step Backward (< Prev)
        Button(
          onClick = onStepBackward,
          enabled = currentStep > 0,
          shape = RoundedCornerShape(12.dp),
          contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
          colors = ButtonDefaults.buttonColors(
            containerColor = Color(0xFF1E293B),
            disabledContainerColor = Color(0xFF020617),
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
          shape = RoundedCornerShape(14.dp),
          contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
          colors = ButtonDefaults.buttonColors(
            containerColor = if (isPlaying) Color(0xFFF59E0B) else Color(0xFF0284C7),
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
          shape = RoundedCornerShape(12.dp),
          contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
          colors = ButtonDefaults.buttonColors(
            containerColor = Color(0xFF10B981),
            disabledContainerColor = Color(0xFF020617),
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

        // Jump to Solved End
        FilledIconButton(
          onClick = { onSeekToStep(totalSteps) },
          enabled = currentStep < totalSteps,
          colors = IconButtonDefaults.filledIconButtonColors(
            containerColor = Color(0xFF1E293B),
            disabledContainerColor = Color(0xFF020617),
          ),
          modifier = Modifier.size(40.dp),
        ) {
          Icon(
            imageVector = Icons.Default.FastForward,
            contentDescription = "Jump to Solved",
            tint = if (currentStep < totalSteps) Color.White else Color(0xFF475569),
          )
        }
      }

      // Speed Selector Row
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Text(
          text = "Animation Speed: ",
          color = Color(0xFF64748B),
          fontSize = 11.sp,
        )
        for (speed in listOf(0.5f to "0.5x Slow", 1.0f to "1x Normal", 2.0f to "2x Fast")) {
          val selected = playbackSpeed == speed.first
          Box(
            modifier = Modifier
              .padding(horizontal = 4.dp)
              .clip(RoundedCornerShape(8.dp))
              .background(if (selected) Color(0xFF1E293B) else Color.Transparent)
              .clickable { onSetPlaybackSpeed(speed.first) }
              .padding(horizontal = 8.dp, vertical = 3.dp),
          ) {
            Text(
              text = speed.second,
              color = if (selected) Color(0xFF38BDF8) else Color(0xFF94A3B8),
              fontSize = 11.sp,
              fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
            )
          }
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
