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
  capturedFaces: Set<CubeFace>,
  liveReticleColors: List<CubeColor>?,
  currentFaceSavedColors: List<CubeColor>,
  stabilityProgress: Float,
  autoSnapEnabled: Boolean,
  redOrangeBiasDegrees: Float = 0f,
  onSelectFace: (CubeFace) -> Unit,
  onToggleAutoSnap: () -> Unit,
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

  Card(
    modifier = modifier.fillMaxWidth(),
    shape = RoundedCornerShape(20.dp),
    colors = CardDefaults.cardColors(containerColor = Color(0xFF0F172A)),
  ) {
    Column(
      modifier = Modifier.padding(14.dp),
      verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
      // Guided 6-Face Stepper Row
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Text(
          text = "Guided 6-Face Camera Scan (${capturedFaces.size}/6)",
          style = MaterialTheme.typography.titleSmall,
          fontWeight = FontWeight.Bold,
          color = Color.White,
        )
        FilterChip(
          selected = autoSnapEnabled,
          onClick = onToggleAutoSnap,
          label = {
            Text(
              text = if (autoSnapEnabled) "Auto-Snap ON" else "Auto-Snap OFF",
              fontSize = 11.sp,
            )
          },
        )
      }

      // Prominent Camera Permission Banner when not yet granted
      if (!hasCameraPermission) {
        Row(
          modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(Color(0xFF1E293B))
            .border(1.dp, Color(0xFF38BDF8), RoundedCornerShape(12.dp))
            .padding(12.dp),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically,
        ) {
          Column(modifier = Modifier.weight(1f)) {
            Text(
              text = "Camera Access Required for Live Scan",
              color = Color.White,
              fontWeight = FontWeight.Bold,
              fontSize = 13.sp,
            )
            Text(
              text = "Tap Allow to scan your physical Rubik's Cube with the camera.",
              color = Color(0xFFCBD5E1),
              fontSize = 11.sp,
            )
          }
          Spacer(modifier = Modifier.width(8.dp))
          Button(
            onClick = { permissionLauncher.launch(Manifest.permission.CAMERA) },
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0284C7)),
          ) {
            Icon(Icons.Default.Videocam, contentDescription = null, modifier = Modifier.size(16.dp))
            Spacer(modifier = Modifier.width(6.dp))
            Text("Grant Camera", fontSize = 12.sp, fontWeight = FontWeight.Bold)
          }
        }
      }

      // 6 Face selector pills in guided scan order
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
      ) {
        for ((idx, face) in CubeFace.GUIDED_SCAN_ORDER.withIndex()) {
          val isSelected = face == targetFace
          val isCaptured = face in capturedFaces
          Box(
            modifier = Modifier
              .weight(1f)
              .clip(RoundedCornerShape(10.dp))
              .background(
                if (isSelected) Color(0xFF1E293B) else Color(0xFF020617)
              )
              .border(
                width = if (isSelected) 2.dp else 1.dp,
                color = if (isSelected) Color(0xFF38BDF8) else Color(0xFF334155),
                shape = RoundedCornerShape(10.dp),
              )
              .clickable { onSelectFace(face) }
              .padding(vertical = 6.dp, horizontal = 4.dp),
            contentAlignment = Alignment.Center,
          ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
              Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(3.dp),
              ) {
                Box(
                  modifier = Modifier
                    .size(10.dp)
                    .clip(CircleShape)
                    .background(face.defaultColor.composeColor)
                )
                Text(
                  text = "${idx + 1}.${face.symbol}",
                  fontSize = 11.sp,
                  fontWeight = FontWeight.Bold,
                  color = Color.White,
                )
              }
              if (isCaptured) {
                Icon(
                  imageVector = Icons.Default.CheckCircle,
                  contentDescription = "Captured",
                  tint = Color(0xFF10B981),
                  modifier = Modifier.size(12.dp),
                )
              }
            }
          }
        }
      }

      // Orientation instruction banner
      Box(
        modifier = Modifier
          .fillMaxWidth()
          .clip(RoundedCornerShape(12.dp))
          .background(Color(0xFF1E293B))
          .padding(horizontal = 12.dp, vertical = 8.dp),
      ) {
        Column {
          Text(
            text = targetFace.scanInstruction,
            color = Color(0xFF38BDF8),
            fontWeight = FontWeight.Bold,
            fontSize = 13.sp,
          )
          Text(
            text = "${targetFace.orientationHint} • Tap any reticle square to flip Red ↔ Orange",
            color = Color(0xFFCBD5E1),
            fontSize = 11.sp,
          )
        }
      }

      // 1:1 Square Camera Viewport + 3x3 Live Reticle Overlay
      val previewColors = liveReticleColors ?: currentFaceSavedColors
      Box(
        modifier = Modifier
          .fillMaxWidth()
          .aspectRatio(1f)
          .clip(RoundedCornerShape(16.dp))
          .background(Color(0xFF020617))
          .border(1.dp, Color(0xFF334155), RoundedCornerShape(16.dp))
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
            targetFace = targetFace,
            onFrameAnalyzed = onFrameAnalyzed,
            modifier = Modifier.fillMaxSize(),
          )
        } else {
          Box(
            modifier = Modifier
              .fillMaxSize()
              .padding(10.dp),
            contentAlignment = Alignment.TopCenter,
          ) {
            Text(
              text = if (!hasCameraPermission) {
                "Tap here to grant Camera Permission (or use Simulate Scan below)"
              } else {
                "Live Camera Paused — Showing 3x3 Reticle Preview"
              },
              color = Color(0xFF94A3B8),
              fontSize = 11.sp,
            )
          }
        }

        // Interactive 3x3 Alignment Reticle Overlay (0.70f matches CubeColorDetector.reticleFraction)
        InteractiveReticleGridOverlay(
          colors = previewColors,
          targetCenterColor = targetFace.defaultColor,
          targetSymbol = targetFace.symbol,
          onCellTap = onReticleCellTap,
          modifier = Modifier.fillMaxSize(0.70f),
        )

        // Camera toggle icon at top-right of viewport
        if (hasCameraPermission) {
          Box(
            modifier = Modifier
              .align(Alignment.TopEnd)
              .padding(8.dp)
              .clip(CircleShape)
              .background(Color.Black.copy(alpha = 0.6f))
              .clickable { cameraActive = !cameraActive }
              .padding(8.dp),
          ) {
            Icon(
              imageVector = if (cameraActive) Icons.Default.Videocam else Icons.Default.VideocamOff,
              contentDescription = "Toggle Camera",
              tint = Color.White,
              modifier = Modifier.size(18.dp),
            )
          }
        }

        // Thin stability progress bar overlaid along the bottom edge of the camera preview
        if (hasCameraPermission && cameraActive) {
          LinearProgressIndicator(
            progress = { stabilityProgress },
            modifier = Modifier
              .align(Alignment.BottomCenter)
              .fillMaxWidth()
              .height(5.dp),
            color = Color(0xFF10B981),
            trackColor = Color.Black.copy(alpha = 0.45f),
          )
        }
      }

      // Action buttons: Capture Face & Simulate Scan directly below the camera preview
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
      ) {
        Button(
          onClick = {
            if (!hasCameraPermission) {
              permissionLauncher.launch(Manifest.permission.CAMERA)
            } else {
              onCaptureFace()
            }
          },
          modifier = Modifier.weight(1f),
          colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0284C7)),
        ) {
          Icon(Icons.Default.CameraAlt, contentDescription = null, modifier = Modifier.size(18.dp))
          Spacer(modifier = Modifier.width(6.dp))
          Text(
            text = if (hasCameraPermission) "Capture ${targetFace.symbol} Face" else "Grant Camera & Scan",
            fontWeight = FontWeight.Bold,
          )
        }

        OutlinedButton(
          onClick = onSimulateScanFace,
          colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFF38BDF8)),
        ) {
          Icon(Icons.Default.AutoAwesome, contentDescription = null, modifier = Modifier.size(16.dp))
          Spacer(modifier = Modifier.width(4.dp))
          Text("Simulate Scan", fontSize = 12.sp)
        }
      }
    }
  }
}

@Composable
private fun InteractiveReticleGridOverlay(
  colors: List<CubeColor>,
  targetCenterColor: CubeColor,
  targetSymbol: String,
  onCellTap: (Int) -> Unit,
  modifier: Modifier = Modifier,
) {
  Column(
    modifier = modifier
      .aspectRatio(1f)
      .border(2.dp, Color.White.copy(alpha = 0.85f), RoundedCornerShape(16.dp))
      .padding(6.dp),
    verticalArrangement = Arrangement.spacedBy(6.dp),
  ) {
    for (row in 0..2) {
      Row(
        modifier = Modifier
          .weight(1f)
          .fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
      ) {
        for (col in 0..2) {
          val idx = row * 3 + col
          val color = if (idx == 4) targetCenterColor else colors.getOrElse(idx) { CubeColor.WHITE }
          Box(
            modifier = Modifier
              .weight(1f)
              .fillMaxSize()
              .clip(RoundedCornerShape(10.dp))
              .border(1.5.dp, Color.White.copy(alpha = 0.5f), RoundedCornerShape(10.dp))
              .clickable(enabled = idx != 4) { onCellTap(idx) },
            contentAlignment = Alignment.Center,
          ) {
            Box(
              modifier = Modifier
                .size(34.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(color.composeColor.copy(alpha = 0.92f))
                .border(1.5.dp, Color.Black.copy(alpha = 0.75f), RoundedCornerShape(8.dp)),
              contentAlignment = Alignment.Center,
            ) {
              Text(
                text = if (idx == 4) targetSymbol else color.shortCode.toString(),
                fontSize = 13.sp,
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
