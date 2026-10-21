/*
 * Copyright 2026
 * Licensed under the Apache License, Version 2.0.
 */
package javax.microedition.shell;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import androidx.annotation.Nullable;
import ru.playsoftware.j2meloader.config.ProfileModel;
import ru.playsoftware.j2meloader.config.ShaderInfo;

/** Immutable Canvas/display settings captured for one MIDlet ClassLoader. */
public final class SessionCanvasConfig {
    private static final Map<ClassLoader, SessionCanvasConfig> SESSIONS = new ConcurrentHashMap<>();

    public final int virtualWidth;
    public final int virtualHeight;
    public final boolean filterBitmap;
    public final boolean touchInput;
    public final int graphicsMode;
    @Nullable public final ShaderInfo shader;
    public final boolean parallelRedraw;
    public final boolean forceFullscreen;
    public final boolean showFps;
    public final int backgroundColor;
    public final int scaleRatio;
    public final int fpsLimit;
    public final int scaleType;
    public final int screenGravity;

    private SessionCanvasConfig(ProfileModel p) {
        virtualWidth = p.screenWidth;
        virtualHeight = p.screenHeight;
        filterBitmap = p.screenFilter;
        touchInput = p.touchInput;
        graphicsMode = p.graphicsMode;
        shader = p.shader;
        parallelRedraw = p.parallelRedrawScreen;
        forceFullscreen = p.forceFullscreen;
        showFps = p.showFps;
        backgroundColor = p.screenBackgroundColor;
        scaleRatio = p.screenScaleRatio;
        fpsLimit = p.fpsLimit;
        scaleType = p.screenScaleType;
        screenGravity = p.screenGravity;
    }

    public static void register(ClassLoader loader, ProfileModel profile) {
        if (loader == null || profile == null) return;
        SESSIONS.put(loader, new SessionCanvasConfig(profile));
    }

    @Nullable public static SessionCanvasConfig forClassLoader(@Nullable ClassLoader loader) {
        return loader == null ? null : SESSIONS.get(loader);
    }

    @Nullable public static SessionCanvasConfig current() {
        return forClassLoader(Thread.currentThread().getContextClassLoader());
    }

    public static void release(ClassLoader loader) {
        if (loader != null) SESSIONS.remove(loader);
    }
}
