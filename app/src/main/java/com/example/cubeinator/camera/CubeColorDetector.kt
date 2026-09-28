package com.example.cubeinator.camera

import android.graphics.Color as AndroidColor
import androidx.camera.core.ImageProxy
import com.example.cubeinator.cube.CubeColor
import com.example.cubeinator.cube.CubeFace
import java.util.EnumMap
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sqrt

/**
 * CIE L*a*b* color representation for perceptually uniform sticker classification.
 */
data class LabColor(
  val l: Float,
  val a: Float,
  val b: Float,
) {
  /**
   * Perceptual hue angle in degrees [0..360) in the (a*, b*) plane.
   * - Red stickers typically lie in ~18°..41° (b* < a* * 0.9)
   * - Orange stickers typically lie in ~44°..68° (b* >= a* * 0.95)
   * - Yellow stickers typically lie in ~70°..108°
   */
  val hueAngleDeg: Float
    get() {
      val deg = atan2(b.toDouble(), a.toDouble()) * (180.0 / PI)
      return ((deg % 360.0 + 360.0) % 360.0).toFloat()
    }

  val chroma: Float
    get() = sqrt(a * a + b * b)

  fun deltaE(other: LabColor): Float {
    val dl = (l - other.l) * 0.65f // Weight chromaticity higher than luminance to resist shadows
    val da = a - other.a
    val db = b - other.b
    return sqrt(dl * dl + da * da + db * db)
  }
}

/**
 * Result of analyzing a single camera frame for the 3x3 Rubik's Cube reticle.
 */
data class ReticleDetectionResult(
  val colors: List<CubeColor>,
  val sampledRgbHex: List<Int>,
  val confidence: Float,
)

/**
 * Extracts and classifies the 3x3 grid of Rubik's Cube sticker colors from a CameraX [ImageProxy].
 * Uses glare-filtered pixel sampling, CIE L*a*b* chromaticity angles, and live center-sticker
 * relative calibration to accurately separate Red vs Orange across diverse lighting conditions.
 */
object CubeColorDetector {

  // Learned CIE L*a*b* references from captured or inspected cube centers
  private val calibratedCenters = EnumMap<CubeColor, LabColor>(CubeColor::class.java)

  /**
   * User-adjustable fine-tuning offset (in Lab hue degrees, typically -8f..+8f).
   * Positive values shift the boundary so more borderline shades classify as RED;
   * negative values shift the boundary so more borderline shades classify as ORANGE.
   */
  @Volatile
  var redOrangeBiasDegrees: Float = 0f

  fun recordCalibratedCenter(color: CubeColor, rgbHex: Int) {
    val r = AndroidColor.red(rgbHex)
    val g = AndroidColor.green(rgbHex)
    val b = AndroidColor.blue(rgbHex)
    calibratedCenters[color] = rgbToLab(r, g, b)
  }

  fun clearCalibration() {
    calibratedCenters.clear()
  }

  /**
   * Analyzes the centered square reticle region (occupying [reticleFraction] of the shorter image dimension)
   * and returns the 9 detected [CubeColor]s in row-major order (0..8) aligned with upright screen coordinates.
   */
  fun analyzeFrame(
    image: ImageProxy,
    targetFace: CubeFace? = null,
    reticleFraction: Float = 0.70f,
  ): ReticleDetectionResult? {
    val planes = image.planes
    if (planes.size < 3) return null

    val width = image.width
    val height = image.height
    val rotationDegrees = image.imageInfo.rotationDegrees

    val yBuffer = planes[0].buffer
    val uBuffer = planes[1].buffer
    val vBuffer = planes[2].buffer

    val yRowStride = planes[0].rowStride
    val yPixelStride = planes[0].pixelStride
    val uvRowStride = planes[1].rowStride
    val uvPixelStride = planes[1].pixelStride

    val minDim = min(width, height)
    val reticleSize = (minDim * reticleFraction).toInt().coerceAtLeast(30)
    val cellSpan = reticleSize / 3f
    val startX = (width - reticleSize) / 2f
    val startY = (height - reticleSize) / 2f

    val cellRigbTriples = Array(9) { IntArray(3) }
    val rgbColors = ArrayList<Int>(9)

    for (screenRow in 0..2) {
      for (screenCol in 0..2) {
        val cellIdx = screenRow * 3 + screenCol
        val (sensorRow, sensorCol) = mapScreenCellToSensor(screenRow, screenCol, rotationDegrees)

        val centerX = (startX + (sensorCol + 0.5f) * cellSpan).toInt()
        val centerY = (startY + (sensorRow + 0.5f) * cellSpan).toInt()
        val sampleRadius = (cellSpan * 0.20f).toInt().coerceIn(3, 14)

        val sampledPixels = ArrayList<IntArray>(64)

        for (dy in -sampleRadius..sampleRadius step 2) {
          for (dx in -sampleRadius..sampleRadius step 2) {
            val px = (centerX + dx).coerceIn(0, width - 1)
            val py = (centerY + dy).coerceIn(0, height - 1)

            val yIdx = py * yRowStride + px * yPixelStride
            val uvIdx = (py / 2) * uvRowStride + (px / 2) * uvPixelStride

            if (yIdx in 0 until yBuffer.limit() &&
              uvIdx in 0 until uBuffer.limit() &&
              uvIdx in 0 until vBuffer.limit()
            ) {
              val yVal = yBuffer.get(yIdx).toInt() and 0xFF
              val uVal = (uBuffer.get(uvIdx).toInt() and 0xFF) - 128
              val vVal = (vBuffer.get(uvIdx).toInt() and 0xFF) - 128

              val r = (yVal + 1.370705f * vVal).toInt().coerceIn(0, 255)
              val g = (yVal - 0.337633f * uVal - 0.698001f * vVal).toInt().coerceIn(0, 255)
              val b = (yVal + 1.732446f * uVal).toInt().coerceIn(0, 255)

              sampledPixels.add(intArrayOf(r, g, b))
            }
          }
        }

        val (repR, repG, repB) = computeGlareResistantRgb(sampledPixels)
        cellRigbTriples[cellIdx] = intArrayOf(repR, repG, repB)
        rgbColors.add(AndroidColor.rgb(repR, repG, repB))
      }
    }

    // Extract live center cell (index 4) Lab color to assist relative Red/Orange separation
    val centerRgb = cellRigbTriples[4]
    val centerLab = rgbToLab(centerRgb[0], centerRgb[1], centerRgb[2])
    if (targetFace != null) {
      // Only update live calibration if the center sticker is reasonably saturated (for non-white faces)
      if (targetFace.defaultColor == CubeColor.RED && centerLab.chroma > 22f) {
        calibratedCenters[CubeColor.RED] = centerLab
      } else if (targetFace.defaultColor == CubeColor.ORANGE && centerLab.chroma > 22f) {
        calibratedCenters[CubeColor.ORANGE] = centerLab
      }
    }

    val detectedColors = ArrayList<CubeColor>(9)
    var totalConfidence = 0f

    for (idx in 0 until 9) {
      val rgb = cellRigbTriples[idx]
      val (color, conf) = classifyRgb(
        r = rgb[0],
        g = rgb[1],
        b = rgb[2],
        targetFace = targetFace,
        liveCenterLab = centerLab,
      )
      detectedColors.add(color)
      totalConfidence += conf
    }

    // Within-frame relative refinement if a face contains multiple warm (Red/Orange) stickers
    val refinedColors = refineWarmStickersInFrame(
      initialColors = detectedColors,
      cellRgb = cellRigbTriples,
      targetFace = targetFace,
    )

    return ReticleDetectionResult(
      colors = refinedColors,
      sampledRgbHex = rgbColors,
      confidence = (totalConfidence / 9f).coerceIn(0f, 1f),
    )
  }

  /**
   * Filters out dark cubie seam pixels and washed-out specular glare pixels when sampling a sticker.
   */
  private fun computeGlareResistantRgb(pixels: List<IntArray>): Triple<Int, Int, Int> {
    if (pixels.isEmpty()) return Triple(200, 200, 200)

    // Filter out very dark gap/border pixels (maxC < 38)
    val nonDark = pixels.filter { maxOf(it[0], it[1], it[2]) >= 38 }
    val candidates = nonDark.ifEmpty { pixels }

    // Measure chroma/saturation of each pixel: (max - min) / max
    val withSat = candidates.map { rgb ->
      val maxC = maxOf(rgb[0], rgb[1], rgb[2]).coerceAtLeast(1)
      val minC = minOf(rgb[0], rgb[1], rgb[2])
      val sat = (maxC - minC).toFloat() / maxC.toFloat()
      rgb to sat
    }.sortedByDescending { it.second }

    // If at least 35% of pixels are chromatic (sat >= 0.24f), average the top 55% most saturated pixels
    // so white specular highlights on part of a Red/Orange sticker don't wash out the hue!
    val chromaticCount = withSat.count { it.second >= 0.24f }
    val selected = if (chromaticCount >= (withSat.size * 0.35f).toInt().coerceAtLeast(1)) {
      val keepCount = (withSat.size * 0.55f).toInt().coerceIn(1, withSat.size)
      withSat.subList(0, keepCount).map { it.first }
    } else {
      // Otherwise it's likely a White sticker; trim extreme outliers
      withSat.map { it.first }
    }

    var rSum = 0
    var gSum = 0
    var bSum = 0
    for (p in selected) {
      rSum += p[0]
      gSum += p[1]
      bSum += p[2]
    }
    val n = selected.size
    return Triple(rSum / n, gSum / n, bSum / n)
  }

  /**
   * Maps upright screen (row, col) in 0..2 to raw camera sensor (row, col) in 0..2.
   * When rotationDegrees == 90 (standard portrait back camera), rotating the sensor 90° CW
   * puts sensorCol along +screenRow and sensorRow along (2 - screenCol).
   */
  internal fun mapScreenCellToSensor(
    row: Int,
    col: Int,
    rotationDegrees: Int,
  ): Pair<Int, Int> {
    return when (((rotationDegrees % 360) + 360) % 360) {
      90 -> (2 - col) to row
      180 -> (2 - row) to (2 - col)
      270 -> col to (2 - row)
      else -> row to col
    }
  }

  /**
   * Classifies an (R, G, B) sample into the closest [CubeColor] along with a [0..1] confidence score.
   * Uses CIE L*a*b* chromaticity angles, baseline-subtracted green/red ratio, and optional center calibration.
   */
  fun classifyRgb(
    r: Int,
    g: Int,
    b: Int,
    targetFace: CubeFace? = null,
    liveCenterLab: LabColor? = null,
  ): Pair<CubeColor, Float> {
    val hsv = FloatArray(3)
    AndroidColor.RGBToHSV(r, g, b, hsv)
    val hue = hsv[0]   // 0..360
    val sat = hsv[1]   // 0..1
    val value = hsv[2] // 0..1

    val lab = rgbToLab(r, g, b)

    // 1. Low saturation & low Lab chroma + moderate-to-high luminance -> WHITE
    if ((sat < 0.23f && value > 0.44f) || (lab.chroma < 16f && lab.l > 50f)) {
      val conf = (1f - sat / 0.25f).coerceIn(0.65f, 1f)
      return CubeColor.WHITE to conf
    }

    // 2. Non-warm colors (Yellow, Green, Blue)
    if (sat >= 0.22f && value >= 0.18f) {
      when {
        // Yellow: high HSV hue (44..78) OR Lab hue > 69° with strong positive b*
        (hue in 45f..78f && lab.hueAngleDeg >= 66f) || (hue in 48f..78f) -> {
          return CubeColor.YELLOW to 0.92f
        }
        // Green
        hue in 78f..168f -> {
          return CubeColor.GREEN to 0.94f
        }
        // Blue
        hue in 168f..272f -> {
          return CubeColor.BLUE to 0.94f
        }
      }
    }

    // 3. Warm colors: RED vs ORANGE vs YELLOW boundary
    if (hue >= 272f || hue <= 52f) {
      return classifyWarmRedOrOrange(
        r = r,
        g = g,
        b = b,
        hsvHue = hue,
        lab = lab,
        targetFace = targetFace,
        liveCenterLab = liveCenterLab,
      )
    }

    // Fallback: perceptual CIE L*a*b* distance to reference colors
    var bestColor = CubeColor.WHITE
    var bestDist = Float.MAX_VALUE
    for (candidate in CubeColor.entries) {
      val (cr, cg, cb) = candidate.refRgb
      val refLab = calibratedCenters[candidate] ?: rgbToLab(cr, cg, cb)
      val dist = lab.deltaE(refLab)
      if (dist < bestDist) {
        bestDist = dist
        bestColor = candidate
      }
    }
    return bestColor to 0.6f
  }

  /**
   * Dedicated separator for Red vs Orange (and borderline Orange-Yellow).
   * Combines:
   * 1. CIE Lab hue angle atan2(b, a):
   *    - Red has strong a and moderate b (angle ~15°..41°, b/a < 0.90), and glare lowers b even further!
   *    - Orange has both strong a and strong b (angle ~44°..68°, b/a >= 0.93), even in shadow!
   * 2. Baseline-subtracted green-to-red ratio: (G - B) / (R - B), which removes white specular glare.
   * 3. Live / stored calibration from the cube's own Red (R) and Orange (L) center stickers.
   */
  private fun classifyWarmRedOrOrange(
    r: Int,
    g: Int,
    b: Int,
    hsvHue: Float,
    lab: LabColor,
    targetFace: CubeFace?,
    liveCenterLab: LabColor?,
  ): Pair<CubeColor, Float> {
    // Wrap HSV hue into signed range [-90..+90] around 0°
    val signedHsvHue = if (hsvHue > 180f) hsvHue - 360f else hsvHue

    // Unmistakable crimson/magenta-red
    if (signedHsvHue < 6f) {
      return CubeColor.RED to 0.96f
    }

    // Borderline Orange-Yellow check
    if (signedHsvHue >= 43f && lab.hueAngleDeg >= 68f) {
      return CubeColor.YELLOW to 0.85f
    }

    // Baseline-subtracted green ratio: removes ambient/glare floor min(b, g)
    val floorB = min(b, g)
    val redSpan = (r - floorB).coerceAtLeast(1)
    val greenExcess = (g - floorB).coerceAtLeast(0)
    val glareFreeGreenRatio = greenExcess.toFloat() / redSpan.toFloat() // 0.0 (pure red) .. 1.0 (yellow)

    // CIE L*a*b* hue angle in degrees (handle negative b* for magenta-red as < 0)
    val rawLabHueDeg = (atan2(lab.b.toDouble(), lab.a.coerceAtLeast(1f).toDouble()) * (180.0 / PI)).toFloat()

    // Check if we have live center reference on the Red (R) or Orange (L) face
    if (targetFace == CubeFace.R && liveCenterLab != null && liveCenterLab.chroma > 20f) {
      val centerLabHue = (atan2(liveCenterLab.b.toDouble(), liveCenterLab.a.coerceAtLeast(1f).toDouble()) * (180.0 / PI)).toFloat()
      val hueDeltaFromRedCenter = rawLabHueDeg - centerLabHue
      val lumaDeltaFromRedCenter = lab.l - liveCenterLab.l
      // On the Red center face, a sticker is only Orange if it is distinctly yellower (+9.5° Lab hue)
      // AND brighter than the Red center sticker under the exact same light
      return if (hueDeltaFromRedCenter >= (9.5f + redOrangeBiasDegrees) && lumaDeltaFromRedCenter >= 2.5f) {
        CubeColor.ORANGE to 0.88f
      } else {
        CubeColor.RED to 0.92f
      }
    }

    if (targetFace == CubeFace.L && liveCenterLab != null && liveCenterLab.chroma > 20f) {
      val centerLabHue = (atan2(liveCenterLab.b.toDouble(), liveCenterLab.a.coerceAtLeast(1f).toDouble()) * (180.0 / PI)).toFloat()
      val hueDeltaFromOrangeCenter = rawLabHueDeg - centerLabHue
      val lumaDeltaFromOrangeCenter = lab.l - liveCenterLab.l
      // On the Orange center face, a sticker is only Red if it is distinctly redder (-9.5° Lab hue)
      // OR much darker/more crimson than the Orange center sticker
      return if (hueDeltaFromOrangeCenter <= (-9.5f + redOrangeBiasDegrees) && lumaDeltaFromOrangeCenter <= -2.0f) {
        CubeColor.RED to 0.88f
      } else {
        CubeColor.ORANGE to 0.92f
      }
    }

    // Check if both Red and Orange centers have been calibrated during this session
    val calRed = calibratedCenters[CubeColor.RED]
    val calOrange = calibratedCenters[CubeColor.ORANGE]
    if (calRed != null && calOrange != null) {
      val redHue = (atan2(calRed.b.toDouble(), calRed.a.coerceAtLeast(1f).toDouble()) * (180.0 / PI)).toFloat()
      val orangeHue = (atan2(calOrange.b.toDouble(), calOrange.a.coerceAtLeast(1f).toDouble()) * (180.0 / PI)).toFloat()
      if (orangeHue > redHue + 5f) {
        val midpointHue = (redHue + orangeHue) * 0.5f + redOrangeBiasDegrees
        return if (rawLabHueDeg < midpointHue) {
          CubeColor.RED to 0.90f
        } else {
          CubeColor.ORANGE to 0.90f
        }
      }
    }

    // Universal CIE L*a*b* + glare-free green ratio decision boundary:
    // - Red typically has rawLabHueDeg < 42.5° and glareFreeGreenRatio < 0.31
    // - Orange typically has rawLabHueDeg >= 42.5° and glareFreeGreenRatio >= 0.31
    // Compute a combined "orangeness" score where 0.0 is the Red/Orange boundary:
    val labThreshold = 42.8f + redOrangeBiasDegrees
    val greenRatioThreshold = 0.315f + (redOrangeBiasDegrees * 0.01f)

    val labScore = (rawLabHueDeg - labThreshold) / 10f
    val ratioScore = (glareFreeGreenRatio - greenRatioThreshold) / 0.10f
    // Also factor in luminance L*: Red pigment is darker (L* ~ 35..52), Orange is brighter (L* ~ 54..72)
    val lumaScore = (lab.l - 52f) / 25f

    val orangeScore = 0.55f * labScore + 0.35f * ratioScore + 0.10f * lumaScore
    val confidence = (0.65f + abs(orangeScore) * 0.25f).coerceIn(0.60f, 0.98f)

    return if (orangeScore >= 0f) {
      CubeColor.ORANGE to confidence
    } else {
      CubeColor.RED to confidence
    }
  }

  /**
   * When multiple warm stickers (Red/Orange) appear on the same 3x3 face under identical lighting,
   * uses their relative Lab hue angle & luminance gap to prevent mixed misclassifications.
   */
  private fun refineWarmStickersInFrame(
    initialColors: List<CubeColor>,
    cellRgb: Array<IntArray>,
    targetFace: CubeFace?,
  ): List<CubeColor> {
    val warmIndices = initialColors.indices.filter {
      initialColors[it] == CubeColor.RED || initialColors[it] == CubeColor.ORANGE
    }
    if (warmIndices.size < 2) return initialColors

    val warmLabs = warmIndices.associateWith { idx ->
      val rgb = cellRgb[idx]
      val lab = rgbToLab(rgb[0], rgb[1], rgb[2])
      val hueDeg = (atan2(lab.b.toDouble(), lab.a.coerceAtLeast(1f).toDouble()) * (180.0 / PI)).toFloat()
      hueDeg
    }

    val minHue = warmLabs.values.minOrNull() ?: return initialColors
    val maxHue = warmLabs.values.maxOrNull() ?: return initialColors
    val spread = maxHue - minHue

    val result = initialColors.toMutableList()
    if (spread >= 11.5f) {
      // Both Red and Orange are genuinely present on this face!
      // Split them cleanly at the midpoint of this frame's min and max warm Lab hue.
      val frameMidpoint = (minHue + maxHue) * 0.5f + (redOrangeBiasDegrees * 0.5f)
      for (idx in warmIndices) {
        val h = warmLabs.getValue(idx)
        result[idx] = if (h < frameMidpoint) CubeColor.RED else CubeColor.ORANGE
      }
    } else if (targetFace == CubeFace.R && 4 in warmIndices) {
      // All warm stickers on this face are within < 11.5° of the Red center -> they are all RED!
      for (idx in warmIndices) {
        result[idx] = CubeColor.RED
      }
    } else if (targetFace == CubeFace.L && 4 in warmIndices) {
      // All warm stickers on this face are within < 11.5° of the Orange center -> they are all ORANGE!
      for (idx in warmIndices) {
        result[idx] = CubeColor.ORANGE
      }
    }

    return result
  }

  /**
   * Converts sRGB (0..255) to CIE L*a*b* (D65 reference white).
   */
  fun rgbToLab(r: Int, g: Int, b: Int): LabColor {
    fun pivotRgb(n: Float): Float {
      return if (n > 0.04045f) ((n + 0.055f) / 1.055f).pow(2.4f) else n / 12.92f
    }

    val rn = pivotRgb(r / 255f)
    val gn = pivotRgb(g / 255f)
    val bn = pivotRgb(b / 255f)

    // sRGB to XYZ (D65)
    val x = (rn * 0.4124564f + gn * 0.3575761f + bn * 0.1804375f) / 0.95047f
    val y = (rn * 0.2126729f + gn * 0.7151522f + bn * 0.0721750f) / 1.00000f
    val z = (rn * 0.0193339f + gn * 0.1191920f + bn * 0.9503041f) / 1.08883f

    fun pivotXyz(t: Float): Float {
      return if (t > 0.008856f) t.pow(1f / 3f) else (7.787f * t) + (16f / 116f)
    }

    val fx = pivotXyz(x)
    val fy = pivotXyz(y)
    val fz = pivotXyz(z)

    val l = (116f * fy - 16f).coerceAtLeast(0f)
    val a = 500f * (fx - fy)
    val bStar = 200f * (fy - fz)
    return LabColor(l, a, bStar)
  }
}
