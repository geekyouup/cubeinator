package com.example.cubeinator.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.cubeinator.cube.CubeColor
import com.example.cubeinator.cube.CubeFace
import com.example.cubeinator.cube.CubeMove
import com.example.cubeinator.cube.CubeState
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

private data class Vec3(val x: Float, val y: Float, val z: Float) {
  operator fun plus(o: Vec3) = Vec3(x + o.x, y + o.y, z + o.z)
  operator fun minus(o: Vec3) = Vec3(x - o.x, y - o.y, z - o.z)
  operator fun times(s: Float) = Vec3(x * s, y * s, z * s)
  fun dot(o: Vec3): Float = x * o.x + y * o.y + z * o.z
  fun cross(o: Vec3): Vec3 = Vec3(
    y * o.z - z * o.y,
    z * o.x - x * o.z,
    x * o.y - y * o.x,
  )
  fun normalized(): Vec3 {
    val len = sqrt(x * x + y * y + z * z)
    return if (len > 1e-5f) Vec3(x / len, y / len, z / len) else this
  }
}

private data class Quad3D(
  val vertices: List<Vec3>,
  val fillColor: Color,
  val strokeColor: Color,
  val isSticker: Boolean,
  val isHighlightedLayer: Boolean,
  val depthBias: Float = 0f,
)

/**
 * Interactive 3D Rubik's Cube renderer with real-time animated face rotations,
 * directional turn arrows, and touch-to-orbit camera controls.
 *
 * @param cubeState The base [CubeState] before [activeMove] is applied.
 * @param activeMove Optional move currently animating (or highlighted).
 * @param moveProgress Animation progress in [0f..1f] for [activeMove].
 * @param yawDegrees Horizontal camera orbit angle in degrees.
 * @param pitchDegrees Vertical camera orbit angle in degrees.
 * @param onOrbitChange Callback invoked when the user drags to rotate the 3D view.
 */
@Composable
fun Cube3DCanvas(
  cubeState: CubeState,
  activeMove: CubeMove? = null,
  moveProgress: Float = 0f,
  highlightedFace: CubeFace? = activeMove?.face,
  capturedFaces: Set<CubeFace> = CubeFace.entries.toSet(),
  yawDegrees: Float = -34f,
  pitchDegrees: Float = 25f,
  onOrbitChange: ((deltaYaw: Float, deltaPitch: Float) -> Unit)? = null,
  modifier: Modifier = Modifier,
) {
  val gestureModifier = if (onOrbitChange != null) {
    Modifier.pointerInput(Unit) {
      detectDragGestures { change, dragAmount ->
        change.consume()
        onOrbitChange(dragAmount.x * 0.45f, dragAmount.y * 0.45f)
      }
    }
  } else {
    Modifier
  }

  Canvas(modifier = modifier.clipToBounds().then(gestureModifier)) {
    val quads = ArrayList<Quad3D>(400)
    val cubieHalf = 0.48f
    val stickerHalf = 0.41f
    val stickerLift = 0.008f

    // Build all 27 cubies (ix, iy, iz in -1..1)
    for (ix in -1..1) {
      for (iy in -1..1) {
        for (iz in -1..1) {
          val inRotatingLayer = activeMove != null && isCubieInFaceLayer(ix, iy, iz, activeMove.face)
          val inHighlightedFace = highlightedFace != null && isCubieInFaceLayer(ix, iy, iz, highlightedFace)
          val layerAngleRad = if (inRotatingLayer) {
            // Clockwise turn around outward face normal corresponds to -turns * 90 deg
            (-activeMove.turns * 90f * moveProgress) * (PI.toFloat() / 180f)
          } else {
            0f
          }
          val layerAxis = if (inRotatingLayer) {
            faceNormal(activeMove.face)
          } else {
            Vec3(0f, 1f, 0f)
          }

          val center = Vec3(ix.toFloat(), iy.toFloat(), iz.toFloat())
          val bodyColor = if (inHighlightedFace) Color(0xFF1E293B) else Color(0xFF0F172A)
          val bodyBorder = if (inHighlightedFace) Color(0xFF475569) else Color(0xFF1E293B)

          // Add the 6 faces of this cubie
          for (localFace in CubeFace.entries) {
            val normal = faceNormal(localFace)
            val (uAxis, vAxis) = faceTangentAxes(localFace)
            val faceCenter = center + normal * cubieHalf

            val b0 = rotateAroundAxis(faceCenter + (uAxis * -cubieHalf) + (vAxis * -cubieHalf), layerAxis, layerAngleRad)
            val b1 = rotateAroundAxis(faceCenter + (uAxis * cubieHalf) + (vAxis * -cubieHalf), layerAxis, layerAngleRad)
            val b2 = rotateAroundAxis(faceCenter + (uAxis * cubieHalf) + (vAxis * cubieHalf), layerAxis, layerAngleRad)
            val b3 = rotateAroundAxis(faceCenter + (uAxis * -cubieHalf) + (vAxis * cubieHalf), layerAxis, layerAngleRad)

            quads.add(
              Quad3D(
                vertices = listOf(b0, b1, b2, b3),
                fillColor = bodyColor,
                strokeColor = bodyBorder,
                isSticker = false,
                isHighlightedLayer = inHighlightedFace,
                depthBias = 0f,
              )
            )

            // Check if this cubie face is on the exterior of the 3x3x3 cube
            val stickerColor = stickerForCubieFace(cubeState, ix, iy, iz, localFace)
            if (stickerColor != null) {
              val isCenterCubie = (ix == 0 && iy == 0) || (ix == 0 && iz == 0) || (iy == 0 && iz == 0)
              val isFaceCaptured = localFace in capturedFaces
              val fill = if (isFaceCaptured || isCenterCubie) {
                stickerColor.composeColor
              } else {
                Color(0xFF64748B)
              }
              val border = when {
                inHighlightedFace -> Color.White.copy(alpha = 0.9f)
                isFaceCaptured || isCenterCubie -> stickerColor.borderColor
                else -> Color(0xFF94A3B8)
              }

              val sCenter = center + normal * (cubieHalf + stickerLift)
              val s0 = rotateAroundAxis(sCenter + (uAxis * -stickerHalf) + (vAxis * -stickerHalf), layerAxis, layerAngleRad)
              val s1 = rotateAroundAxis(sCenter + (uAxis * stickerHalf) + (vAxis * -stickerHalf), layerAxis, layerAngleRad)
              val s2 = rotateAroundAxis(sCenter + (uAxis * stickerHalf) + (vAxis * stickerHalf), layerAxis, layerAngleRad)
              val s3 = rotateAroundAxis(sCenter + (uAxis * -stickerHalf) + (vAxis * stickerHalf), layerAxis, layerAngleRad)

              quads.add(
                Quad3D(
                  vertices = listOf(s0, s1, s2, s3),
                  fillColor = fill,
                  strokeColor = border,
                  isSticker = true,
                  isHighlightedLayer = inHighlightedFace,
                  depthBias = 0.03f,
                )
              )
            }
          }
        }
      }
    }

    val yawRad = yawDegrees * (PI.toFloat() / 180f)
    val pitchRad = pitchDegrees * (PI.toFloat() / 180f)
    val cameraDist = 8.2f
    val scale = size.minDimension * 0.165f
    val centerX = size.width / 2f
    val centerY = size.height / 2f

    fun transformToCamera(p: Vec3): Vec3 {
      // Rotate around Y (yaw), then around X (pitch)
      val x1 = p.x * cos(yawRad) + p.z * sin(yawRad)
      val z1 = -p.x * sin(yawRad) + p.z * cos(yawRad)
      val y1 = p.y

      val y2 = y1 * cos(pitchRad) - z1 * sin(pitchRad)
      val z2 = y1 * sin(pitchRad) + z1 * cos(pitchRad)
      return Vec3(x1, y2, z2)
    }

    fun projectToScreen(camP: Vec3): Offset {
      val perspective = cameraDist / (cameraDist - camP.z).coerceAtLeast(1f)
      return Offset(
        x = centerX + camP.x * scale * perspective,
        y = centerY - camP.y * scale * perspective,
      )
    }

    // Transform & backface-cull quads
    data class ProjectedQuad(
      val screenPoints: List<Offset>,
      val sortDepth: Float,
      val shadedFill: Color,
      val strokeColor: Color,
      val isSticker: Boolean,
      val isHighlightedLayer: Boolean,
    )

    val lightDir = Vec3(0.35f, 0.65f, 0.75f).normalized()
    val visibleQuads = ArrayList<ProjectedQuad>(quads.size)

    for (q in quads) {
      val camVerts = q.vertices.map(::transformToCamera)
      val v0 = camVerts[0]
      val v1 = camVerts[1]
      val v2 = camVerts[2]
      val normal = (v1 - v0).cross(v2 - v0).normalized()
      // Camera is at (0, 0, +cameraDist) looking toward -Z
      val viewVec = Vec3(-v0.x, -v0.y, cameraDist - v0.z).normalized()
      if (normal.dot(viewVec) <= 0.01f) continue

      val avgZ = (camVerts[0].z + camVerts[1].z + camVerts[2].z + camVerts[3].z) * 0.25f + q.depthBias
      val diffuse = normal.dot(lightDir).coerceIn(0f, 1f)
      val shadeFactor = if (q.isSticker) {
        0.84f + 0.16f * diffuse
      } else {
        0.65f + 0.35f * diffuse
      }

      val shadedFill = Color(
        red = (q.fillColor.red * shadeFactor).coerceIn(0f, 1f),
        green = (q.fillColor.green * shadeFactor).coerceIn(0f, 1f),
        blue = (q.fillColor.blue * shadeFactor).coerceIn(0f, 1f),
        alpha = q.fillColor.alpha,
      )

      visibleQuads.add(
        ProjectedQuad(
          screenPoints = camVerts.map(::projectToScreen),
          sortDepth = avgZ,
          shadedFill = shadedFill,
          strokeColor = q.strokeColor,
          isSticker = q.isSticker,
          isHighlightedLayer = q.isHighlightedLayer,
        )
      )
    }

    // Painter's algorithm: back-to-front by Z depth
    visibleQuads.sortBy { it.sortDepth }

    val path = Path()
    for (pq in visibleQuads) {
      path.reset()
      path.moveTo(pq.screenPoints[0].x, pq.screenPoints[0].y)
      path.lineTo(pq.screenPoints[1].x, pq.screenPoints[1].y)
      path.lineTo(pq.screenPoints[2].x, pq.screenPoints[2].y)
      path.lineTo(pq.screenPoints[3].x, pq.screenPoints[3].y)
      path.close()

      drawPath(path = path, color = pq.shadedFill)
      drawPath(
        path = path,
        color = pq.strokeColor,
        style = Stroke(
          width = if (pq.isSticker && pq.isHighlightedLayer) 3.2f else 1.8f,
          join = StrokeJoin.Round,
        ),
      )
    }

    // Draw 3D directional turn arrow overlay above the active face
    if (activeMove != null) {
      drawFaceTurnArrow(
        move = activeMove,
        transformToCamera = ::transformToCamera,
        projectToScreen = ::projectToScreen,
      )
    }
  }
}

private fun DrawScope.drawFaceTurnArrow(
  move: CubeMove,
  transformToCamera: (Vec3) -> Vec3,
  projectToScreen: (Vec3) -> Offset,
) {
  val normal = faceNormal(move.face)
  val (uAxis, vAxis) = faceTangentAxes(move.face)
  val planeCenter = normal * 1.62f
  val radius = 0.85f

  // In (uAxis, vAxis) with outward normal = uAxis x vAxis:
  // Clockwise when looking at the face from outside sweeps from +vAxis toward +uAxis (angle decreasing).
  val startDeg = 135f
  val sweepDeg = when (move.turns) {
    1 -> -150f   // CW
    -1 -> 150f   // CCW
    2 -> -240f   // 180 double turn
    else -> -150f
  }

  val segments = 20
  val points = ArrayList<Offset>(segments + 1)
  for (i in 0..segments) {
    val t = i.toFloat() / segments
    val deg = startDeg + sweepDeg * t
    val rad = deg * (PI.toFloat() / 180f)
    val p3d = planeCenter + (uAxis * (radius * cos(rad))) + (vAxis * (radius * sin(rad)))
    points.add(projectToScreen(transformToCamera(p3d)))
  }

  val arcPath = Path()
  points.forEachIndexed { idx, pt ->
    if (idx == 0) arcPath.moveTo(pt.x, pt.y) else arcPath.lineTo(pt.x, pt.y)
  }

  // Outer glow + crisp arrow stroke
  drawPath(
    path = arcPath,
    color = Color.Black.copy(alpha = 0.65f),
    style = Stroke(width = 12f, cap = StrokeCap.Round, join = StrokeJoin.Round),
  )
  drawPath(
    path = arcPath,
    color = Color(0xFF38BDF8),
    style = Stroke(width = 6.5f, cap = StrokeCap.Round, join = StrokeJoin.Round),
  )

  // Arrowhead at the end of the arc
  if (points.size >= 2) {
    val tip = points.last()
    val prev = points[points.size - 2]
    val dx = tip.x - prev.x
    val dy = tip.y - prev.y
    val len = sqrt(dx * dx + dy * dy).coerceAtLeast(1f)
    val ux = dx / len
    val uy = dy / len
    val px = -uy
    val py = ux
    val headSize = 18f

    val wing1 = Offset(tip.x - ux * headSize + px * headSize * 0.65f, tip.y - uy * headSize + py * headSize * 0.65f)
    val wing2 = Offset(tip.x - ux * headSize - px * headSize * 0.65f, tip.y - uy * headSize - py * headSize * 0.65f)

    val headPath = Path().apply {
      moveTo(tip.x, tip.y)
      lineTo(wing1.x, wing1.y)
      lineTo(wing2.x, wing2.y)
      close()
    }
    drawPath(path = headPath, color = Color.Black.copy(alpha = 0.7f), style = Stroke(width = 5f, join = StrokeJoin.Round))
    drawPath(path = headPath, color = Color(0xFF38BDF8))

    // Draw floating move notation badge (e.g. "U'" or "L2") right next to the direction arrow
    val centerScreen = projectToScreen(transformToCamera(normal * 1.68f))
    val tailScreen = points.first()
    val badgeX = (centerScreen.x * 0.6f + tailScreen.x * 0.4f)
      .coerceIn(28.dp.toPx(), (size.width - 28.dp.toPx()).coerceAtLeast(28.dp.toPx()))
    val badgeY = (centerScreen.y * 0.6f + tailScreen.y * 0.4f)
      .coerceIn(18.dp.toPx(), (size.height - 18.dp.toPx()).coerceAtLeast(18.dp.toPx()))

    val pillW = 38.dp.toPx()
    val pillH = 24.dp.toPx()
    val pillTopLeft = Offset(badgeX - pillW / 2f, badgeY - pillH / 2f)
    val pillCorner = CornerRadius(8.dp.toPx(), 8.dp.toPx())
    val faceColor = move.face.defaultColor

    // Dark outer shadow + face-colored pill + crisp border
    drawRoundRect(
      color = Color.Black.copy(alpha = 0.75f),
      topLeft = Offset(pillTopLeft.x - 1.5f, pillTopLeft.y - 1.5f),
      size = Size(pillW + 3f, pillH + 3f),
      cornerRadius = pillCorner,
    )
    drawRoundRect(
      color = faceColor.composeColor,
      topLeft = pillTopLeft,
      size = Size(pillW, pillH),
      cornerRadius = pillCorner,
    )
    drawRoundRect(
      color = Color(0xFF38BDF8),
      topLeft = pillTopLeft,
      size = Size(pillW, pillH),
      cornerRadius = pillCorner,
      style = Stroke(width = 2.dp.toPx()),
    )

    val textColor = if (faceColor == CubeColor.WHITE || faceColor == CubeColor.YELLOW) {
      Color(0xFF0F172A).toArgb()
    } else {
      Color.White.toArgb()
    }
    val textPaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
      color = textColor
      textAlign = android.graphics.Paint.Align.CENTER
      textSize = 13.sp.toPx()
      typeface = android.graphics.Typeface.create(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD)
    }
    val textOffsetY = (textPaint.descent() + textPaint.ascent()) / 2f
    drawContext.canvas.nativeCanvas.drawText(
      move.notation,
      badgeX,
      badgeY - textOffsetY,
      textPaint,
    )
  }
}

private fun isCubieInFaceLayer(ix: Int, iy: Int, iz: Int, face: CubeFace): Boolean = when (face) {
  CubeFace.U -> iy == 1
  CubeFace.D -> iy == -1
  CubeFace.R -> ix == 1
  CubeFace.L -> ix == -1
  CubeFace.F -> iz == 1
  CubeFace.B -> iz == -1
}

private fun faceNormal(face: CubeFace): Vec3 = when (face) {
  CubeFace.U -> Vec3(0f, 1f, 0f)
  CubeFace.D -> Vec3(0f, -1f, 0f)
  CubeFace.R -> Vec3(1f, 0f, 0f)
  CubeFace.L -> Vec3(-1f, 0f, 0f)
  CubeFace.F -> Vec3(0f, 0f, 1f)
  CubeFace.B -> Vec3(0f, 0f, -1f)
}

/**
 * Right-handed tangent axes (uAxis, vAxis) on each face such that uAxis x vAxis == faceNormal(face).
 */
private fun faceTangentAxes(face: CubeFace): Pair<Vec3, Vec3> = when (face) {
  CubeFace.F -> Vec3(1f, 0f, 0f) to Vec3(0f, 1f, 0f)
  CubeFace.B -> Vec3(-1f, 0f, 0f) to Vec3(0f, 1f, 0f)
  CubeFace.R -> Vec3(0f, 0f, -1f) to Vec3(0f, 1f, 0f)
  CubeFace.L -> Vec3(0f, 0f, 1f) to Vec3(0f, 1f, 0f)
  CubeFace.U -> Vec3(1f, 0f, 0f) to Vec3(0f, 0f, -1f)
  CubeFace.D -> Vec3(1f, 0f, 0f) to Vec3(0f, 0f, 1f)
}

private fun rotateAroundAxis(p: Vec3, axis: Vec3, angleRad: Float): Vec3 {
  if (angleRad == 0f) return p
  val c = cos(angleRad)
  val s = sin(angleRad)
  // Rodrigues' rotation formula
  return (p * c) + (axis.cross(p) * s) + (axis * (axis.dot(p) * (1f - c)))
}

/**
 * Maps a cubie at (ix, iy, iz) in {-1, 0, 1}^3 and an exterior [face] to its sticker color in [cubeState].
 */
private fun stickerForCubieFace(
  cubeState: CubeState,
  ix: Int,
  iy: Int,
  iz: Int,
  face: CubeFace,
): CubeColor? {
  return when (face) {
    CubeFace.U -> if (iy == 1) cubeState.stickerAt(CubeFace.U, row = iz + 1, col = ix + 1) else null
    CubeFace.D -> if (iy == -1) cubeState.stickerAt(CubeFace.D, row = 1 - iz, col = ix + 1) else null
    CubeFace.F -> if (iz == 1) cubeState.stickerAt(CubeFace.F, row = 1 - iy, col = ix + 1) else null
    CubeFace.B -> if (iz == -1) cubeState.stickerAt(CubeFace.B, row = 1 - iy, col = 1 - ix) else null
    CubeFace.R -> if (ix == 1) cubeState.stickerAt(CubeFace.R, row = 1 - iy, col = 1 - iz) else null
    CubeFace.L -> if (ix == -1) cubeState.stickerAt(CubeFace.L, row = 1 - iy, col = iz + 1) else null
  }
}

/**
 * 2D Unfolded Cube Net view showing all 6 faces (U, L, F, R, B, D) in a standard cross layout:
 *       [U]
 *   [L] [F] [R] [B]
 *       [D]
 */
@Composable
fun Cube2DNetView(
  cubeState: CubeState,
  highlightedFace: CubeFace? = null,
  capturedFaces: Set<CubeFace> = CubeFace.entries.toSet(),
  onStickerClick: ((face: CubeFace, indexInFace: Int) -> Unit)? = null,
  cellSize: Dp = 18.dp,
  modifier: Modifier = Modifier,
) {
  val faceGap = if (cellSize < 14.dp) 3.dp else 4.dp
  val faceWidth = cellSize * 3 + 10.dp
  Column(
    modifier = modifier,
    horizontalAlignment = Alignment.CenterHorizontally,
    verticalArrangement = Arrangement.spacedBy(faceGap),
  ) {
    // Top row: U aligned over F
    Row(
      horizontalArrangement = Arrangement.spacedBy(faceGap),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      Spacer(modifier = Modifier.width(faceWidth))
      MiniFaceGrid(
        face = CubeFace.U,
        stickers = cubeState.faceStickers(CubeFace.U),
        isHighlighted = highlightedFace == CubeFace.U,
        isCaptured = CubeFace.U in capturedFaces,
        cellSize = cellSize,
        onStickerClick = onStickerClick,
      )
      Spacer(modifier = Modifier.width(faceWidth))
      Spacer(modifier = Modifier.width(faceWidth))
    }

    // Middle row: L, F, R, B
    Row(
      horizontalArrangement = Arrangement.spacedBy(faceGap),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      for (face in listOf(CubeFace.L, CubeFace.F, CubeFace.R, CubeFace.B)) {
        MiniFaceGrid(
          face = face,
          stickers = cubeState.faceStickers(face),
          isHighlighted = highlightedFace == face,
          isCaptured = face in capturedFaces,
          cellSize = cellSize,
          onStickerClick = onStickerClick,
        )
      }
    }

    // Bottom row: D aligned under F
    Row(
      horizontalArrangement = Arrangement.spacedBy(faceGap),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      Spacer(modifier = Modifier.width(faceWidth))
      MiniFaceGrid(
        face = CubeFace.D,
        stickers = cubeState.faceStickers(CubeFace.D),
        isHighlighted = highlightedFace == CubeFace.D,
        isCaptured = CubeFace.D in capturedFaces,
        cellSize = cellSize,
        onStickerClick = onStickerClick,
      )
      Spacer(modifier = Modifier.width(faceWidth))
      Spacer(modifier = Modifier.width(faceWidth))
    }
  }
}

@Composable
fun MiniFaceGrid(
  face: CubeFace,
  stickers: List<CubeColor>,
  isHighlighted: Boolean,
  cellSize: Dp,
  isCaptured: Boolean = true,
  onStickerClick: ((face: CubeFace, indexInFace: Int) -> Unit)? = null,
  modifier: Modifier = Modifier,
) {
  val borderColor = if (isHighlighted) Color(0xFF059669) else Color(0xFF475569)
  val bgColor = if (isHighlighted) Color(0xFF064E3B) else Color(0xFF1E293B)

  Column(
    modifier = modifier
      .clip(RoundedCornerShape(6.dp))
      .background(bgColor)
      .border(width = if (isHighlighted) 2.dp else 1.dp, color = borderColor, shape = RoundedCornerShape(6.dp))
      .padding(3.dp),
    verticalArrangement = Arrangement.spacedBy(2.dp),
  ) {
    for (row in 0..2) {
      Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
        for (col in 0..2) {
          val idx = row * 3 + col
          val color = stickers[idx]
          val showColor = isCaptured || idx == 4
          Box(
            modifier = Modifier
              .size(cellSize)
              .clip(RoundedCornerShape(3.dp))
              .background(if (showColor) color.composeColor else Color(0xFF64748B))
              .border(
                0.75.dp,
                if (showColor) color.borderColor else Color(0xFF94A3B8),
                RoundedCornerShape(3.dp),
              )
              .then(
                if (onStickerClick != null) {
                  Modifier.clickable { onStickerClick(face, idx) }
                } else {
                  Modifier
                }
              ),
            contentAlignment = Alignment.Center,
          ) {
            if (idx == 4) {
              Text(
                text = face.symbol,
                fontSize = (cellSize.value * 0.52f).sp,
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
