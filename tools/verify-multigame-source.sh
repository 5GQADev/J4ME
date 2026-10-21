#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
LAUNCHER="$ROOT/app/src/main/java/javax/microedition/shell/MultiGameSessionLauncher.java"
THREAD="$ROOT/app/src/main/java/javax/microedition/shell/MidletThread.java"
CANVAS="$ROOT/app/src/main/java/javax/microedition/lcdui/Canvas.java"
GRID="$ROOT/app/src/main/java/javax/microedition/shell/multigame/MultiGameGridView.java"
ACTIVITY="$ROOT/app/src/main/java/javax/microedition/shell/multigame/MultiGameActivity.java"
require() { grep -Fq "$2" "$1" || { echo "FAIL: missing '$2' in $1" >&2; exit 1; }; }
require "$LAUNCHER" 'volatile boolean hostPaused;'
require "$LAUNCHER" 'reservation.hostPaused = paused;'
require "$LAUNCHER" 'thread.setSessionHostPaused(true);'
require "$LAUNCHER" 'reservation.cancelled'
require "$THREAD" 'sessionHostPaused'
require "$THREAD" 'sessionHostBound'
require "$THREAD" 'sessionCleanupStarted'
require "$CANVAS" 'setRenderFpsLimit'
require "$GRID" 'stopSession()'
require "$ACTIVITY" 'onDestroy()'
echo 'PASS: multi-game lifecycle/render source invariants found.'
