/*
 * Copyright 2026
 * Licensed under the Apache License, Version 2.0.
 */
package javax.microedition.shell;

import android.content.Context;

import java.io.File;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;


import javax.microedition.lcdui.Display;
import javax.microedition.lcdui.overlay.OverlayView;

/** Starts one converted MIDlet package in a specific multi-game slot. */
public final class MultiGameSessionLauncher {
    private static final Map<Display.SessionViewHost, MidletThread> ACTIVE_SESSIONS =
            new ConcurrentHashMap<>();
    private static final Map<Display.SessionViewHost, LaunchReservation> LAUNCHING =
            new ConcurrentHashMap<>();

    private static final class LaunchReservation {
        volatile boolean cancelled;
        volatile boolean hostPaused;
        volatile MidletThread thread;
    }

    private MultiGameSessionLauncher() { }

    /** Request stop even before a MIDlet thread has been created or its owner has been attached. */
    public static boolean stop(Display.SessionViewHost host) {
        if (host == null) return false;
        boolean found = false;
        MidletThread thread = ACTIVE_SESSIONS.get(host);
        if (thread != null) {
            thread.requestSessionStop();
            found = true;
        }
        LaunchReservation reservation = LAUNCHING.get(host);
        if (reservation != null) {
            reservation.cancelled = true;
            MidletThread pendingThread = reservation.thread;
            if (pendingThread != null) pendingThread.requestSessionStop();
            found = true;
        }
        return found;
    }

    /** True while a slot has either a launch reservation or a live runtime thread. */
    public static boolean hasSessionOrLaunching(Display.SessionViewHost host) {
        return host != null && (ACTIVE_SESSIONS.containsKey(host) || LAUNCHING.containsKey(host));
    }

    /** Propagate Activity background state even while a slot's MIDlet is still initializing. */
    public static void setHostPaused(Display.SessionViewHost host, boolean paused) {
        if (host == null) return;
        // Record lifecycle state even before createSession() has registered its thread.
        // Otherwise a background transition during package/profile preparation could be lost.
        LaunchReservation reservation = LAUNCHING.get(host);
        if (reservation != null) {
            reservation.hostPaused = paused;
            MidletThread pendingThread = reservation.thread;
            if (pendingThread != null) pendingThread.setSessionHostPaused(paused);
        }
        MidletThread thread = ACTIVE_SESSIONS.get(host);
        if (thread != null) thread.setSessionHostPaused(paused);
    }

    static void registerSession(Display.SessionViewHost host, MidletThread thread) {
        if (host == null || thread == null) return;
        ACTIVE_SESSIONS.put(host, thread);
        LaunchReservation reservation = LAUNCHING.get(host);
        if (reservation != null) {
            reservation.thread = thread;
            if (reservation.cancelled) {
                thread.requestSessionStop();
            } else if (reservation.hostPaused) {
                // Preserve an onPause() that happened before the runtime thread existed.
                thread.setSessionHostPaused(true);
            }
        }
        // The INIT thread can fail very quickly; don't retain a session that already exited.
        if (thread.isSessionFinished()) ACTIVE_SESSIONS.remove(host, thread);
    }

    static void releaseSession(Display.SessionViewHost host, MidletThread thread) {
        if (host != null && thread != null) ACTIVE_SESSIONS.remove(host, thread);
    }

    /**
     * Loads the slot's profile and MIDlet descriptor, then creates an independent MIDlet thread.
     * The session host is bound on the UI thread before startApp is called.
     */
    public static String launch(Context activityContext, String appPath,
                                Display.SessionViewHost host, OverlayView overlay)
            throws Exception {
        return launch(activityContext, appPath, host, overlay, null);
    }

    /** Launch a requested MIDlet class when the source package selected one during import. */
    public static String launch(Context activityContext, String appPath,
                                Display.SessionViewHost host, OverlayView overlay,
                                String requestedMidletClass) throws Exception {
        if (activityContext == null || host == null || overlay == null) {
            throw new NullPointerException("session context, host and overlay are required");
        }
        if (ACTIVE_SESSIONS.containsKey(host)) {
            throw new IOException("This slot already has a running or starting game");
        }
        LaunchReservation reservation = new LaunchReservation();
        if (LAUNCHING.putIfAbsent(host, reservation) != null) {
            throw new IOException("This slot is already launching a game");
        }
        boolean sessionCreated = false;
        try {
            File appDir = new File(appPath);
            if (!appDir.isDirectory()) throw new IOException("Converted runtime directory not found: " + appPath);
            MicroLoader loader = new MicroLoader(activityContext, appPath, host, overlay);
            if (!loader.initForSession()) {
                throw new IOException("No saved game profile. Create the slot profile before launch.");
            }
            LinkedHashMap<String, String> midlets = loader.loadMIDletList();
            if (midlets.isEmpty()) throw new IOException("No MIDlet entry found in the runtime descriptor");
            Map.Entry<String, String> selected = null;
            if (requestedMidletClass != null && !requestedMidletClass.trim().isEmpty()) {
                String requested = requestedMidletClass.trim();
                selected = midlets.containsKey(requested)
                        ? new java.util.AbstractMap.SimpleImmutableEntry<>(requested, midlets.get(requested))
                        : null;
                if (selected == null) {
                    throw new IOException("Selected MIDlet class '" + requested
                            + "' is not present in the converted runtime descriptor. Re-convert the JAR.");
                }
            }
            if (selected == null) selected = midlets.entrySet().iterator().next();
            if (reservation.cancelled) throw new IOException("Launch cancelled");
            MidletThread.createSession(loader, selected.getKey());
            sessionCreated = true;
            return selected.getValue();
        } finally {
            // Keep the reservation only until the session thread has been registered.
            // If preparation failed or Stop won the race, release it so the slot can retry.
            LAUNCHING.remove(host, reservation);
            if (!sessionCreated && reservation.thread != null) {
                reservation.thread.requestSessionStop();
            }
        }
    }
}
