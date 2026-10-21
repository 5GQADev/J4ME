/*
 * Copyright 2026
 * Licensed under the Apache License, Version 2.0.
 */
package javax.microedition.shell.multigame;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Thread-safe four-slot registry for the multi-game host.
 *
 * This class deliberately does not start MIDlets itself: the current runtime still has
 * process-wide Display, ContextHolder, AppClassLoader and MidletThread state. The registry
 * provides a deterministic slot/state contract for the host while those runtime globals are
 * isolated in a later change.
 */
public final class MultiGameSessionRegistry {
    public static final int SLOT_COUNT = 4;

    public enum State { EMPTY, SELECTED, LOADING, RUNNING, PAUSED, FAILED }

    public static final class GameSpec {
        @NonNull public final String title;
        @NonNull public final String appPath;
        @Nullable public final String startArguments;

        public GameSpec(@NonNull String title, @NonNull String appPath,
                        @Nullable String startArguments) {
            if (title.trim().isEmpty()) throw new IllegalArgumentException("title is empty");
            if (appPath.trim().isEmpty()) throw new IllegalArgumentException("appPath is empty");
            this.title = title;
            this.appPath = appPath;
            this.startArguments = startArguments;
        }

        public boolean isPathValid() {
            return new File(appPath).exists();
        }
    }

    public static final class SlotSnapshot {
        public final int slot;
        @NonNull public final State state;
        @Nullable public final GameSpec game;
        @Nullable public final String error;

        private SlotSnapshot(int slot, State state, GameSpec game, String error) {
            this.slot = slot;
            this.state = state;
            this.game = game;
            this.error = error;
        }
    }

    public interface Listener {
        void onSlotChanged(@NonNull SlotSnapshot snapshot);
    }

    private static final class Slot {
        State state = State.EMPTY;
        GameSpec game;
        String error;
        SlotSnapshot snapshot(int index) { return new SlotSnapshot(index, state, game, error); }
    }

    private final Slot[] slots = { new Slot(), new Slot(), new Slot(), new Slot() };
    @Nullable private Listener listener;

    public synchronized void setListener(@Nullable Listener listener) {
        this.listener = listener;
    }

    public synchronized void select(int index, @NonNull GameSpec game) {
        Slot slot = slot(index);
        slot.game = game;
        slot.error = null;
        slot.state = State.SELECTED;
        notifyChanged(index, slot);
    }

    public synchronized void markLoading(int index) {
        Slot slot = requireGame(index);
        requireState(slot, State.SELECTED, State.FAILED, State.PAUSED);
        slot.error = null;
        slot.state = State.LOADING;
        notifyChanged(index, slot);
    }

    public synchronized void markRunning(int index) {
        Slot slot = requireGame(index);
        requireState(slot, State.LOADING, State.PAUSED);
        slot.error = null;
        slot.state = State.RUNNING;
        notifyChanged(index, slot);
    }

    public synchronized void markPaused(int index) {
        Slot slot = requireGame(index);
        requireState(slot, State.RUNNING, State.LOADING);
        slot.state = State.PAUSED;
        notifyChanged(index, slot);
    }

    public synchronized void markFailed(int index, @NonNull Throwable error) {
        Slot slot = requireGame(index);
        slot.state = State.FAILED;
        slot.error = error.getMessage() == null ? error.getClass().getSimpleName() : error.getMessage();
        notifyChanged(index, slot);
    }

    public synchronized void clear(int index) {
        Slot slot = slot(index);
        slot.state = State.EMPTY;
        slot.game = null;
        slot.error = null;
        notifyChanged(index, slot);
    }

    @NonNull public synchronized SlotSnapshot get(int index) {
        return slot(index).snapshot(index);
    }

    @NonNull public synchronized List<SlotSnapshot> snapshot() {
        List<SlotSnapshot> result = new ArrayList<>(SLOT_COUNT);
        for (int i = 0; i < SLOT_COUNT; i++) result.add(slots[i].snapshot(i));
        return Collections.unmodifiableList(result);
    }

    private Slot requireGame(int index) {
        Slot slot = slot(index);
        if (slot.game == null || slot.state == State.EMPTY) {
            throw new IllegalStateException("Slot " + index + " has no selected game");
        }
        return slot;
    }

    private Slot slot(int index) {
        if (index < 0 || index >= SLOT_COUNT) throw new IndexOutOfBoundsException("slot=" + index);
        return slots[index];
    }

    private static void requireState(Slot slot, State... allowed) {
        for (State state : allowed) if (slot.state == state) return;
        throw new IllegalStateException("Invalid slot transition from " + slot.state);
    }

    private void notifyChanged(int index, Slot slot) {
        Listener current = listener;
        if (current != null) current.onSlotChanged(slot.snapshot(index));
    }
}
