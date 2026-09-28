# Cubeinator

An Android app built with Jetpack Compose and CameraX that scans a 3×3 Rubik's Cube using the camera, renders an interactive 3D and 2D net representation of the cube, and demonstrates the quickest step-by-step solution using Kociemba's Two-Phase optimal solver ($\le 21$ moves).

## Features

- **Camera Face Scanner**: Real-time 3×3 reticle overlay with CIE $L^*a^*b^*$ chromaticity analysis, center-sticker relative calibration, glare filtering, and 5-frame temporal stability lock.
- **Manual Touch-Up Editor**: Tap any sticker on the current face or full 2D unfolded cube net to adjust colors before solving.
- **Kociemba Two-Phase Optimal Solver**: Computes shortest-path solutions ($\le 21$ moves in ~10ms) with full physical cube state validation (sticker counts, edge/corner permutation parity, and orientation invariants).
- **Interactive 3D & 2D Step-by-Step Walkthrough**:
  - Real-time 3D perspective Rubik's Cube with animated face layer turns, 3D directional turn arrows, and touch-to-orbit camera controls.
  - Three visualizer modes: **3D Cube**, **3D + 2D Net**, and **2D Net**.
  - Bidirectional playback controls: step forward, step backward (animating exact inverse turns), play/pause with adjustable speed (`0.5x`–`2x`), and interactive timeline scrubbing.

## Building & Running

```bash
./gradlew assembleDebug
```
