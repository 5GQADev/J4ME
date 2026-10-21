/*
 * Copyright 2026 OpenAI
 * Licensed under the Apache License, Version 2.0.
 */
package javax.microedition.shell.multigame;

import android.view.KeyEvent;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/** Routes hardware keys to the selected slot and releases held keys when focus changes. */
public final class MultiGameInputRouter {
    public interface KeyTarget {
        boolean dispatchGameKey(@NonNull KeyEvent event);
        /** Called when this target becomes or stops being the hardware-input target. */
        void onInputFocusChanged(boolean focused);
    }

    private static final Map<ClassLoader, KeyTarget> TARGETS = new HashMap<>();
    private static final Map<ClassLoader, Set<Integer>> PRESSED_KEYS = new HashMap<>();
    @Nullable private static ClassLoader focusedLoader;

    private MultiGameInputRouter() { }

    public static void register(@NonNull ClassLoader loader, @NonNull KeyTarget target) {
        KeyTarget previousFocus;
        boolean focusNew;
        synchronized (MultiGameInputRouter.class) {
            TARGETS.put(loader, target);
            focusNew = focusedLoader == null;
            if (focusNew) focusedLoader = loader;
            previousFocus = focusedLoader == loader ? target : null;
        }
        if (previousFocus != null) previousFocus.onInputFocusChanged(true);
    }

    public static void unregister(@NonNull ClassLoader loader, @Nullable KeyTarget target) {
        KeyTarget oldTarget;
        KeyTarget newTarget = null;
        boolean wasFocused;
        synchronized (MultiGameInputRouter.class) {
            oldTarget = TARGETS.get(loader);
            if (oldTarget != target) return;
            TARGETS.remove(loader);
            wasFocused = focusedLoader == loader;
            if (wasFocused) {
                focusedLoader = TARGETS.isEmpty() ? null : TARGETS.keySet().iterator().next();
                newTarget = focusedLoader == null ? null : TARGETS.get(focusedLoader);
            }
        }
        releasePressedKeys(loader, oldTarget);
        if (wasFocused && oldTarget != null) oldTarget.onInputFocusChanged(false);
        if (wasFocused && newTarget != null) newTarget.onInputFocusChanged(true);
    }

    /** Select the slot receiving hardware input. Held keys are released on the old slot. */
    public static void focus(@Nullable ClassLoader loader) {
        if (loader == null) return;
        KeyTarget oldTarget;
        KeyTarget newTarget;
        ClassLoader oldLoader;
        synchronized (MultiGameInputRouter.class) {
            if (!TARGETS.containsKey(loader) || focusedLoader == loader) return;
            oldLoader = focusedLoader;
            oldTarget = oldLoader == null ? null : TARGETS.get(oldLoader);
            newTarget = TARGETS.get(loader);
        }
        // Release while the old slot is still focused. The synthetic ACTION_UP events
        // travel through Canvas callbacks, which themselves call focus(oldLoader).
        if (oldLoader != null) releasePressedKeys(oldLoader, oldTarget);
        synchronized (MultiGameInputRouter.class) {
            // The target may have been unregistered while release callbacks ran.
            if (!TARGETS.containsKey(loader)) return;
            focusedLoader = loader;
            newTarget = TARGETS.get(loader);
        }
        if (oldTarget != null) oldTarget.onInputFocusChanged(false);
        if (newTarget != null) newTarget.onInputFocusChanged(true);
    }

    public static boolean dispatchToFocused(@NonNull KeyEvent event) {
        KeyTarget target;
        ClassLoader loader;
        synchronized (MultiGameInputRouter.class) {
            loader = focusedLoader;
            target = loader == null ? null : TARGETS.get(loader);
            if (target != null) {
                if (event.getAction() == KeyEvent.ACTION_DOWN) {
                    PRESSED_KEYS.computeIfAbsent(loader, ignored -> new HashSet<>()).add(event.getKeyCode());
                } else if (event.getAction() == KeyEvent.ACTION_UP) {
                    Set<Integer> pressed = PRESSED_KEYS.get(loader);
                    if (pressed != null) {
                        pressed.remove(event.getKeyCode());
                        if (pressed.isEmpty()) PRESSED_KEYS.remove(loader);
                    }
                }
            }
        }
        return target != null && target.dispatchGameKey(event);
    }

    /** Release held hardware keys for every slot when the host Activity loses foreground. */
    public static void releaseAllPressedKeys() {
        Map<ClassLoader, KeyTarget> targets;
        synchronized (MultiGameInputRouter.class) {
            targets = new HashMap<>(TARGETS);
        }
        for (Map.Entry<ClassLoader, KeyTarget> entry : targets.entrySet()) {
            releasePressedKeys(entry.getKey(), entry.getValue());
        }
    }

    private static void releasePressedKeys(@NonNull ClassLoader loader, @Nullable KeyTarget target) {
        if (target == null) return;
        Set<Integer> keys;
        synchronized (MultiGameInputRouter.class) {
            Set<Integer> pressed = PRESSED_KEYS.remove(loader);
            keys = pressed == null ? new HashSet<>() : new HashSet<>(pressed);
        }
        long now = System.currentTimeMillis();
        for (Integer keyCode : new ArrayList<>(keys)) {
            KeyEvent up = new KeyEvent(now, now, KeyEvent.ACTION_UP, keyCode, 0);
            target.dispatchGameKey(up);
        }
    }

    @Nullable public static synchronized ClassLoader getFocusedLoader() {
        return focusedLoader;
    }

    public static synchronized boolean isFocused(@Nullable ClassLoader loader) {
        return loader != null && focusedLoader == loader;
    }
}
