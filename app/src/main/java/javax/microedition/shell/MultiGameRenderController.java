/*
 * Copyright 2026 OpenAI
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package javax.microedition.shell;

import androidx.annotation.Nullable;

import javax.microedition.lcdui.Canvas;

/**
 * Coordinates presentation-rate policy for the four canvases in a future multi-game host.
 *
 * <p>This class deliberately controls only Canvas presentation requests. It does not pause,
 * sleep, or change priorities of MIDlet/game threads. A host must register each session's
 * Canvas and own the lifecycle of the corresponding MIDlet independently.</p>
 */
public final class MultiGameRenderController {
    public static final int SLOT_COUNT = 4;
    public static final int GRID_FPS = 4;
    public static final int UNLIMITED_FPS = 0;

    private final Canvas[] canvases = new Canvas[SLOT_COUNT];
    private int maximizedSlot = -1;

    /** Register or replace the Canvas assigned to a slot. Null unregisters the slot. */
    public synchronized void setCanvas(int slot, @Nullable Canvas canvas) {
        checkSlot(slot);
        Canvas previous = canvases[slot];
        if (previous != null && previous != canvas) {
            previous.setRenderFpsLimit(UNLIMITED_FPS);
        }
        canvases[slot] = canvas;
        applyPolicy(slot);
    }

    /** Enter 2x2 grid mode; all registered canvases are presented at up to 4 FPS. */
    public synchronized void showGrid() {
        maximizedSlot = -1;
        applyAllPolicies();
    }

    /** Maximize one slot; its Canvas is unrestricted while the other slots remain at 4 FPS. */
    public synchronized void maximize(int slot) {
        checkSlot(slot);
        maximizedSlot = slot;
        applyAllPolicies();
    }

    /** Return from maximized mode to the 2x2 grid. */
    public synchronized void restoreGrid() {
        showGrid();
    }

    public synchronized int getMaximizedSlot() {
        return maximizedSlot;
    }

    @Nullable
    public synchronized Canvas getCanvas(int slot) {
        checkSlot(slot);
        return canvases[slot];
    }

    private void applyAllPolicies() {
        for (int i = 0; i < SLOT_COUNT; i++) {
            applyPolicy(i);
        }
    }

    private void applyPolicy(int slot) {
        Canvas canvas = canvases[slot];
        if (canvas == null) {
            return;
        }
        canvas.setRenderFpsLimit(maximizedSlot == slot ? UNLIMITED_FPS : GRID_FPS);
    }

    private static void checkSlot(int slot) {
        if (slot < 0 || slot >= SLOT_COUNT) {
            throw new IndexOutOfBoundsException("slot must be in [0, 3]: " + slot);
        }
    }
}
