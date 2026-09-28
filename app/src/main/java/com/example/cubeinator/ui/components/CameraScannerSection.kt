package com.example.cubeinator.ui.components

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material.icons.filled.VideocamOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.cubeinator.camera.CubeColorDetector
import com.example.cubeinator.camera.ReticleDetectionResult
import com.example.cubeinator.cube.CubeColor
import com.example.cubeinator.cube.CubeFace
import java.util.concurrent.Executors

@Composable
fun CameraScannerSection(
  targetFace: CubeFace,
  liveDetectedFace: CubeFace? = null,
  capturedFaces: Set<CubeFace>,
  liveReticleColors: List<CubeColor>?,
  currentFaceSavedColors: List<CubeColor>,
  isCubeFaceVisibleInCamera: Boolean = false,
  stabilityProgress: Float,
  autoSnapEnabled: Boolean,
  lastAutocorrectMessage: String? = null,
  redOrangeBiasDegrees: Float = 0f,
  onSelectFace: (CubeFace) -> Unit,
  onToggleAutoSnap: () -> Unit,
  onStartFreshScan: () -> Unit = {},
  onAdjustRedOrangeBias: (Float) -> Unit = {},
  onReticleCellTap: (Int) -> Unit = {},
  onFrameAnalyzed: (ReticleDetectionResult) -> Unit,
  onCaptureFace: () -> Unit,
  onSimulateScanFace: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val context = LocalContext.current
  val lifecycleOwner = LocalLifecycleOwner.current
  var hasCameraPermission by remember {
    mutableStateOf(
      ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
        PackageManager.PERMISSION_GRANTED
    )
  }
  var cameraActive by remember { mutableStateOf(true) }
  var hasRequestedPermissionOnLaunch by remember { mutableStateOf(false) }

  val permissionLauncher = rememberLauncherForActivityResult(
    contract = ActivityResultContracts.RequestPermission()
  ) { granted ->
    hasCameraPermission = granted
    if (granted) cameraActive = true
  }

  // Automatically ask for camera permission when the scanner screen opens
  androidx.compose.runtime.LaunchedEffect(Unit) {
    val currentlyGranted =
      ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
        PackageManager.PERMISSION_GRANTED
    hasCameraPermission = currentlyGranted
    if (!currentlyGranted && !hasRequestedPermissionOnLaunch) {
      hasRequestedPermissionOnLaunch = true
      permissionLauncher.launch(Manifest.permission.CAMERA)
    }
  }

  // Re-check camera permission whenever the activity resumes (e.g. returning from system settings)
  DisposableEffect(lifecycleOwner) {
    val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
      if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
        hasCameraPermission =
          ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
            PackageManager.PERMISSION_GRANTED
      }
    }
    lifecycleOwner.lifecycle.addObserver(observer)
    onDispose {
      lifecycleOwner.lifecycle.removeObserver(observer)
    }
  }

  val activeFaceInReticle = liveDetectedFace ?: targetFace
  val remainingFaces = CubeFace.GUIDED_SCAN_ORDER.filter { it !in capturedFaces }
  val showDetectedStickers = isCubeFaceVisibleInCamera || activeFaceInReticle in capturedFaces

  Card(
    modifier = modifier
      .fillMaxWidth()
      .border(1.5.dp, Color(0xFFA7F3D0), RoundedCornerShape(22.dp)),
    shape = RoundedCornerShape(22.dp),
    colors = CardDefaults.cardColors(containerColor = Color(0xFFF0FDF4)),
    elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
  ) {
    Column(
      modifier = Modifier.padding(12.dp),
      verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
      // Header Row: Single-line Progress Title (fixed height, never wraps)
      Text(
        text = when {
          remainingFaces.isEmpty() -> "Cube Scanned (6/6 Faces)"
          else -> "Face Capture (${capturedFaces.size}/6 Faces)"
        },
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.ExtraBold,
        color = if (remainingFaces.isEmpty()) Color(0xFF059669) else Color(0xFF064E3B),
      )

      // Prominent Camera Permission Banner when not yet granted
      if (!hasCameraPermission) {
        Row(
          modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(Color(0xFFDCFCE7))
            .border(1.dp, Color(0xFF10B981), RoundedCornerShape(12.dp))
            .padding(10.dp),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically,
        ) {
          Column(modifier = Modifier.weight(1f)) {
            Text(
              text = "Camera Access Required",
              color = Color(0xFF064E3B),
              fontWeight = FontWeight.Bold,
              fontSize = 12.sp,
            )
            Text(
              text = "Tap Allow to auto-detect your Rubik's Cube as you rotate it.",
              color = Color(0xFF047857),
              fontSize = 11.sp,
            )
          }
          Spacer(modifier = Modifier.width(8.dp))
          Button(
            onClick = { permissionLauncher.launch(Manifest.permission.CAMERA) },
            colors = ButtonDefaults.buttonColors(
              containerColor = Color(0xFF059669),
              contentColor = Color.White,
            ),
          ) {
            Icon(Icons.Default.Videocam, contentDescription = null, modifier = Modifier.size(16.dp), tint = Color.White)
            Spacer(modifier = Modifier.width(6.dp))
            Text("Allow", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color.White)
          }
        }
      }

      // 6 Face status pills (shows checkmarks as faces are auto-detected)
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(5.dp),
      ) {
        for (face in CubeFace.GUIDED_SCAN_ORDER) {
          val isLiveDetected = isCubeFaceVisibleInCamera && face == activeFaceInReticle
          val isCaptured = face in capturedFaces
          Box(
            modifier = Modifier
              .weight(1f)
              .clip(RoundedCornerShape(10.dp))
              .background(
                when {
                  isLiveDetected -> Color(0xFFE0F2FE)
                  isCaptured -> Color(0xFFD1FAE5)
                  else -> Color.White
                }
              )
              .border(
                width = if (isLiveDetected) 2.dp else 1.25.dp,
                color = when {
                  isLiveDetected -> Color(0xFF0284C7)
                  isCaptured -> Color(0xFF059669)
                  else -> Color(0xFF86EFAC)
                },
                shape = RoundedCornerShape(10.dp),
              )
              .clickable { onSelectFace(face) }
              .padding(vertical = 5.dp, horizontal = 3.dp),
            contentAlignment = Alignment.Center,
          ) {
            Row(
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(3.dp),
            ) {
              Box(
                modifier = Modifier
                  .size(10.dp)
                  .clip(CircleShape)
                  .background(face.defaultColor.composeColor)
                  .border(0.75.dp, face.defaultColor.borderColor, CircleShape)
              )
              Text(
                text = face.symbol,
                fontSize = 11.sp,
                fontWeight = FontWeight.ExtraBold,
                color = Color(0xFF064E3B),
              )
              if (isCaptured) {
                Icon(
                  imageVector = Icons.Default.CheckCircle,
                  contentDescription = "Detected",
                  tint = Color(0xFF059669),
                  modifier = Modifier.size(11.dp),
                )
              }
            }
          }
        }
      }

      // Compact Centered Square Camera Viewport + 3x3 Live Reticle Overlay
      val previewColors = liveReticleColors ?: currentFaceSavedColors
      Box(
        modifier = Modifier.fillMaxWidth(),
        contentAlignment = Alignment.Center,
      ) {
        Box(
          modifier = Modifier
            .size(205.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(Color(0xFF064E3B))
            .border(
              width = 2.5.dp,
              color = when {
                autoSnapEnabled && isCubeFaceVisibleInCamera -> Color(0xFF10B981)
                autoSnapEnabled -> Color(0xFF0284C7)
                else -> Color(0xFF6EE7B7)
              },
              shape = RoundedCornerShape(18.dp),
            )
            .then(
              if (!hasCameraPermission) {
                Modifier.clickable { permissionLauncher.launch(Manifest.permission.CAMERA) }
              } else {
                Modifier
              }
            ),
          contentAlignment = Alignment.Center,
        ) {
          if (hasCameraPermission && cameraActive) {
            LiveCameraPreviewWithAnalyzer(
              targetFace = activeFaceInReticle,
              onFrameAnalyzed = onFrameAnalyzed,
              modifier = Modifier.fillMaxSize(),
            )
          } else {
            Box(
              modifier = Modifier
                .fillMaxSize()
                .padding(8.dp),
              contentAlignment = Alignment.TopCenter,
            ) {
              Text(
                text = if (!hasCameraPermission) {
                  "Tap to grant Camera Permission"
                } else {
                  "Camera Paused"
                },
                color = Color(0xFFA7F3D0),
                fontSize = 10.sp,
              )
            }
          }

          // Interactive 3x3 Alignment Reticle Overlay (0.70f matches CubeColorDetector.reticleFraction)
          InteractiveReticleGridOverlay(
            colors = previewColors,
            showDetectedStickers = showDetectedStickers,
            targetCenterColor = activeFaceInReticle.defaultColor,
            targetSymbol = activeFaceInReticle.symbol,
            onCellTap = onReticleCellTap,
            modifier = Modifier.fillMaxSize(0.70f),
          )

          // Thin stability progress bar overlaid along the bottom edge of the camera preview
          if (hasCameraPermission && cameraActive && autoSnapEnabled) {
            LinearProgressIndicator(
              progress = { stabilityProgress },
              modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .height(5.dp),
              color = Color(0xFF34D399),
              trackColor = Color.Black.copy(alpha = 0.45f),
            )
          }
        }
      }

      // Action Row: Scanning On/Off Button + Reset Button side by side
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Button(
          onClick = {
            if (!hasCameraPermission) {
              permissionLauncher.launch(Manifest.permission.CAMERA)
            } else {
              onToggleAutoSnap()
            }
          },
          modifier = Modifier.weight(1f),
          shape = RoundedCornerShape(14.dp),
          colors = ButtonDefaults.buttonColors(
            containerColor = if (autoSnapEnabled) Color(0xFF059669) else Color(0xFF0284C7),
            contentColor = Color.White,
          ),
        ) {
          Icon(
            imageVector = if (autoSnapEnabled) Icons.Default.Videocam else Icons.Default.VideocamOff,
            contentDescription = null,
            tint = Color.White,
            modifier = Modifier.size(18.dp),
          )
          Spacer(modifier = Modifier.width(6.dp))
          Text(
            text = when {
              !hasCameraPermission -> "Grant Camera & Scan"
              autoSnapEnabled -> "Scanning: ON"
              remainingFaces.isEmpty() -> "Scanning: OFF (Rescan)"
              else -> "Scanning: OFF (Start)"
            },
            color = Color.White,
            fontWeight = FontWeight.Bold,
            fontSize = 13.sp,
          )
        }

        Button(
          onClick = onStartFreshScan,
          shape = RoundedCornerShape(14.dp),
          colors = ButtonDefaults.buttonColors(
            containerColor = Color(0xFFF59E0B),
            contentColor = Color.White,
          ),
        ) {
          Icon(
            imageVector = Icons.Default.Refresh,
            contentDescription = "Reset Scan",
            tint = Color.White,
            modifier = Modifier.size(16.dp),
          )
          Spacer(modifier = Modifier.width(4.dp))
          Text(
            text = "Reset",
            color = Color.White,
            fontWeight = FontWeight.Bold,
            fontSize = 13.sp,
          )
        }
      }
    }
  }
}

@Composable
private fun InteractiveReticleGridOverlay(
  colors: List<CubeColor>,
  showDetectedStickers: Boolean,
  targetCenterColor: CubeColor,
  targetSymbol: String,
  onCellTap: (Int) -> Unit,
  modifier: Modifier = Modifier,
) {
  Column(
    modifier = modifier
      .aspectRatio(1f)
      .border(2.dp, Color.White.copy(alpha = 0.85f), RoundedCornerShape(14.dp))
      .padding(4.dp),
    verticalArrangement = Arrangement.spacedBy(4.dp),
  ) {
    for (row in 0..2) {
      Row(
        modifier = Modifier
          .weight(1f)
          .fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
      ) {
        for (col in 0..2) {
          val idx = row * 3 + col
          val color = if (idx == 4) targetCenterColor else colors.getOrElse(idx) { CubeColor.WHITE }
          Box(
            modifier = Modifier
              .weight(1f)
              .fillMaxSize()
              .clip(RoundedCornerShape(8.dp))
              .border(1.25.dp, Color.White.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
              .clickable(enabled = idx != 4 && showDetectedStickers) { onCellTap(idx) },
            contentAlignment = Alignment.Center,
          ) {
            if (showDetectedStickers) {
              Box(
                modifier = Modifier
                  .size(24.dp)
                  .clip(RoundedCornerShape(6.dp))
                  .background(color.composeColor.copy(alpha = 0.92f))
                  .border(1.25.dp, Color.Black.copy(alpha = 0.75f), RoundedCornerShape(6.dp)),
                contentAlignment = Alignment.Center,
              ) {
                Text(
                  text = if (idx == 4) targetSymbol else color.shortCode.toString(),
                  fontSize = 11.sp,
                  fontWeight = FontWeight.ExtraBold,
                  color = if (color == CubeColor.WHITE || color == CubeColor.YELLOW) {
                    Color(0xFF0F172A)
                  } else {
                    Color.White
                  },
                )
              }
            }
          }
        }
      }
    }
  }
}

@Composable
private fun LiveCameraPreviewWithAnalyzer(
  targetFace: CubeFace,
  onFrameAnalyzed: (ReticleDetectionResult) -> Unit,
  modifier: Modifier = Modifier,
) {
  val context = LocalContext.current
  val lifecycleOwner = LocalLifecycleOwner.current
  val currentTargetFace by androidx.compose.runtime.rememberUpdatedState(targetFace)
  val currentOnFrameAnalyzed by androidx.compose.runtime.rememberUpdatedState(onFrameAnalyzed)
  val analysisExecutor = remember { Executors.newSingleThreadExecutor() }
  val previewView = remember {
    PreviewView(context).apply {
      scaleType = PreviewView.ScaleType.FILL_CENTER
      implementationMode = PreviewView.ImplementationMode.COMPATIBLE
    }
  }

  DisposableEffect(lifecycleOwner) {
    val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
    val listener = Runnable {
      runCatching {
        val cameraProvider = cameraProviderFuture.get()
        val preview = Preview.Builder().build().also {
          it.surfaceProvider = previewView.surfaceProvider
        }
        val imageAnalysis = ImageAnalysis.Builder()
          .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
          .build()
          .also { analysis ->
            analysis.setAnalyzer(analysisExecutor) { imageProxy ->
              try {
                val detected = CubeColorDetector.analyzeFrame(
                  image = imageProxy,
                  targetFace = currentTargetFace,
                  reticleFraction = 0.70f,
                )
                if (detected != null) {
                  currentOnFrameAnalyzed(detected)
                }
              } finally {
                imageProxy.close()
              }
            }
          }

        cameraProvider.unbindAll()
        cameraProvider.bindToLifecycle(
          lifecycleOwner,
          CameraSelector.DEFAULT_BACK_CAMERA,
          preview,
          imageAnalysis,
        )
      }
    }
    cameraProviderFuture.addListener(listener, ContextCompat.getMainExecutor(context))

    onDispose {
      runCatching {
        cameraProviderFuture.get().unbindAll()
      }
      analysisExecutor.shutdown()
    }
  }

  AndroidView(
    factory = { previewView },
    modifier = modifier,
  )
}
