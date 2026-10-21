/*
 * Copyright 2026 OpenAI
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */
package javax.microedition.shell.multigame;

import androidx.appcompat.app.AlertDialog;
import android.content.Context;
import android.graphics.Color;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import javax.microedition.lcdui.Display;
import javax.microedition.lcdui.Displayable;
import javax.microedition.shell.MultiGameRenderController;
import javax.microedition.shell.MultiGameSessionLauncher;
import javax.microedition.shell.MidletThread;
import javax.microedition.lcdui.Alert;
import javax.microedition.lcdui.overlay.OverlayView;
import javax.microedition.midlet.MIDlet;
import javax.microedition.util.ContextHolder;

/**
 * Reusable 2x2 presentation surface for four independent MIDlet Display hosts.
 * A controller/Activity is still responsible for creating and starting each MIDlet.
 */
public final class MultiGameGridView extends FrameLayout {
    public static final int SLOT_COUNT = 4;

    private final LinearLayout grid;
    private final LinearLayout[] rows = new LinearLayout[2];
    private final SlotHost[] slots = new SlotHost[SLOT_COUNT];
    private final MultiGameRenderController renderController = new MultiGameRenderController();
    private final FrameLayout maximizedContainer;
    private int maximizedSlot = -1;

    public MultiGameGridView(Context context) {
        super(context);
        setBackgroundColor(Color.BLACK);
        renderController.showGrid();

        grid = new LinearLayout(context);
        grid.setOrientation(LinearLayout.VERTICAL);
        LayoutParams gridParams = new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT);
        addView(grid, gridParams);

        for (int r = 0; r < 2; r++) {
            rows[r] = new LinearLayout(context);
            rows[r].setOrientation(LinearLayout.HORIZONTAL);
            grid.addView(rows[r], new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, 0, 1f));
        }

        for (int i = 0; i < SLOT_COUNT; i++) {
            slots[i] = new SlotHost(context, i);
            LinearLayout.LayoutParams cell = new LinearLayout.LayoutParams(0, LayoutParams.MATCH_PARENT, 1f);
            cell.setMargins(1, 1, 1, 1);
            rows[i / 2].addView(slots[i], cell);
        }

        maximizedContainer = new FrameLayout(context);
        maximizedContainer.setVisibility(GONE);
        addView(maximizedContainer, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));
    }

    /** Attach the display host for this MIDlet to a specific slot. */
    public void bind(int slot, MIDlet midlet) {
        checkSlot(slot);
        if (midlet == null) throw new NullPointerException("midlet");
        slots[slot].bind(midlet);
    }

    /** Detach a slot's MIDlet host. Does not destroy the MIDlet itself. */
    public void unbind(int slot) {
        checkSlot(slot);
        slots[slot].unbind();
    }

    public SlotHost getSlot(int slot) {
        checkSlot(slot);
        return slots[slot];
    }

    public int getMaximizedSlot() {
        return maximizedSlot;
    }

    public void maximize(int slot) {
        checkSlot(slot);
        if (maximizedSlot == slot) return;
        restoreGrid();
        SlotHost host = slots[slot];
        ViewGroup oldParent = (ViewGroup) host.getParent();
        if (oldParent != null) oldParent.removeView(host);
        maximizedContainer.addView(host, new FrameLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));
        grid.setVisibility(GONE);
        maximizedContainer.setVisibility(VISIBLE);
        maximizedSlot = slot;
        renderController.maximize(slot);
        host.setMaximized(true);
    }

    public void restoreGrid() {
        if (maximizedSlot < 0) return;
        int slot = maximizedSlot;
        SlotHost host = slots[slot];
        if (host.getParent() instanceof ViewGroup) ((ViewGroup) host.getParent()).removeView(host);
        LinearLayout.LayoutParams cell = new LinearLayout.LayoutParams(0, LayoutParams.MATCH_PARENT, 1f);
        cell.setMargins(1, 1, 1, 1);
        rows[slot / 2].addView(host, slot % 2, cell);
        host.setMaximized(false);
        renderController.restoreGrid();
        maximizedContainer.setVisibility(GONE);
        grid.setVisibility(VISIBLE);
        maximizedSlot = -1;
    }

    private static void checkSlot(int slot) {
        if (slot < 0 || slot >= SLOT_COUNT) throw new IndexOutOfBoundsException("slot=" + slot);
    }

    /** One slot is both a Display.SessionViewHost and a container for its current Displayable. */
    public final class SlotHost extends FrameLayout implements Display.SessionViewHost, MultiGameInputRouter.KeyTarget {
        private final int slot;
        private final FrameLayout content;
        private final OverlayView overlay;
        private final TextView header;
        private MIDlet owner;
        private Displayable current;
        private boolean isMaximized;
        private String selectedGameName;

        private SlotHost(Context context, int slot) {
            super(context);
            this.slot = slot;
            setBackgroundColor(Color.rgb(24, 24, 24));
            content = new FrameLayout(context);
            addView(content, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));
            overlay = new OverlayView(context, null);
            addView(overlay, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));
            header = new TextView(context);
            header.setTextColor(Color.WHITE);
            header.setTextSize(12);
            header.setGravity(Gravity.CENTER_VERTICAL);
            header.setPadding(8, 4, 8, 4);
            header.setBackgroundColor(0x99000000);
            LayoutParams hp = new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT, Gravity.TOP);
            addView(header, hp);
            updateHeader();
            setOnClickListener(v -> toggleMaximized());
            // The child Canvas/view normally consumes touch events, so put the expand action
            // on the always-visible header as well as the host container.
            header.setOnClickListener(v -> toggleMaximized());
        }

        private void toggleMaximized() {
            if (maximizedSlot == slot) restoreGrid(); else maximize(slot);
        }

        private void bind(MIDlet midlet) {
            if (owner != null && owner != midlet) {
                Display.unregisterSessionHost(owner, this);
                if (current != null) current.clearDisplayableView();
                current = null;
                content.removeAllViews();
            }
            owner = midlet;
            ClassLoader loader = midlet.getClass().getClassLoader();
            ContextHolder.bindSessionContext(loader, getContext());
            ContextHolder.setSessionOverlay(loader, overlay);
            MultiGameInputRouter.register(loader, this);
            Display.registerSessionHost(midlet, this);
            updateHeader();
        }

        private void unbind() {
            renderController.setCanvas(slot, null);
            if (owner != null) {
                ClassLoader loader = owner.getClass().getClassLoader();
                MultiGameInputRouter.unregister(loader, this);
                Display.unregisterSessionHost(owner, this);
                ContextHolder.releaseSession(loader);
            }
            owner = null;
            if (current != null) current.clearDisplayableView();
            current = null;
            content.removeAllViews();
            updateHeader();
        }

        public OverlayView getOverlayView() { return overlay; }

        public boolean hasOwner() { return owner != null; }

        public void stopSession() {
            // Stop can happen before onSessionCreated() binds an owner. In that window the
            // launcher registry still has the HandlerThread, so cancel it directly.
            boolean pendingOrRegistered = MultiGameSessionLauncher.stop(this);
            MIDlet midlet = owner;
            if (midlet != null && !pendingOrRegistered) MidletThread.destroyApp(midlet);
        }

        /** Forward Android host lifecycle to this slot only. */
        public void pauseSession() {
            // This also records the background state when the MIDlet is still initializing.
            MultiGameSessionLauncher.setHostPaused(this, true);
            MIDlet midlet = owner;
            if (midlet != null) MidletThread.pauseApp(midlet);
        }

        /** Resume this slot after the multi-game Activity returns to foreground. */
        public void resumeSession() {
            // The launcher can resume a pending INIT session even before owner is attached.
            MultiGameSessionLauncher.setHostPaused(this, false);
            MIDlet midlet = owner;
            if (midlet != null) MidletThread.resumeApp(midlet);
        }

        @Override
        public void onSessionCreated(MIDlet midlet) {
            if (getContext() instanceof MultiGameActivity) {
                MultiGameActivity activity = (MultiGameActivity) getContext();
                if (activity.isFinishing() || activity.isDestroyed()) {
                    MidletThread.destroyApp(midlet);
                    return;
                }
                bind(midlet);
                activity.onSlotRuntimeStarted(slot, midlet);
            } else {
                bind(midlet);
            }
        }

        @Override
        public void onSessionDestroyed(MIDlet midlet) {
            if (owner == midlet) unbind();
            if (getContext() instanceof MultiGameActivity) {
                MultiGameActivity activity = (MultiGameActivity) getContext();
                if (!activity.isFinishing() && !activity.isDestroyed()) activity.onSlotRuntimeStopped(slot);
            }
        }

        @Override
        public void onSessionFailed(Throwable error) {
            post(() -> {
                if (owner != null) unbind();
                if (getContext() instanceof MultiGameActivity) {
                    MultiGameActivity activity = (MultiGameActivity) getContext();
                    if (!activity.isFinishing() && !activity.isDestroyed()) activity.onSlotRuntimeFailed(slot, error);
                }
            });
        }

        public void setSelectedGameName(String name) {
            selectedGameName = name;
            updateHeader();
        }

        private void updateHeader() {
            String title = owner != null ? owner.getClass().getSimpleName()
                    : (selectedGameName == null || selectedGameName.trim().isEmpty()
                    ? "Choose game · Slot " + (slot + 1) : selectedGameName);
            boolean focused = owner != null && MultiGameInputRouter.isFocused(owner.getClass().getClassLoader());
            header.setText((focused ? "● INPUT  ·  " : "") + title
                    + (isMaximized ? "  ·  Back to grid" : "  ·  Tap to expand"));
            header.setBackgroundColor(focused ? 0xDD176B3A : 0x99000000);
        }

        @Override
        public boolean dispatchTouchEvent(android.view.MotionEvent event) {
            if (event.getActionMasked() == android.view.MotionEvent.ACTION_DOWN && owner != null) {
                MultiGameInputRouter.focus(owner.getClass().getClassLoader());
            }
            return super.dispatchTouchEvent(event);
        }

        @Override
        public void onInputFocusChanged(boolean focused) {
            if (!focused) {
                // Release virtual-keyboard/touch state before another slot receives input.
                Displayable displayable = current;
                if (displayable instanceof javax.microedition.lcdui.Canvas) {
                    ((javax.microedition.lcdui.Canvas) displayable).cancelSessionInput();
                }
            }
            post(this::updateHeader);
        }

        private void setMaximized(boolean value) {
            isMaximized = value;
            updateHeader();
        }

        @Override
        public boolean dispatchGameKey(KeyEvent event) {
            Displayable target = current;
            if (target == null) return false;
            Thread thread = Thread.currentThread();
            ClassLoader previousLoader = thread.getContextClassLoader();
            try {
                if (owner != null) thread.setContextClassLoader(owner.getClass().getClassLoader());
                View view = target.getDisplayableView();
                return view.dispatchKeyEvent(event);
            } catch (RuntimeException ignored) {
                return false;
            } finally {
                thread.setContextClassLoader(previousLoader);
            }
        }

        @Override
        public void onDisplayableChanged(Displayable previous, Displayable next) {
            if (owner == null) return;
            if (current != null && current != next) current.clearDisplayableView();
            renderController.setCanvas(slot, next instanceof javax.microedition.lcdui.Canvas
                    ? (javax.microedition.lcdui.Canvas) next : null);
            current = next;
            content.removeAllViews();
            if (next != null) {
                // View construction must resolve ContextHolder's session binding even though
                // this callback runs on the Android main thread, whose TCCL is not the MIDlet.
                Thread thread = Thread.currentThread();
                ClassLoader previousLoader = thread.getContextClassLoader();
                try {
                    if (owner != null) thread.setContextClassLoader(owner.getClass().getClassLoader());
                    View view = next.getDisplayableView();
                    if (view.getParent() instanceof ViewGroup) ((ViewGroup) view.getParent()).removeView(view);
                    content.addView(view, new FrameLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));
                } finally {
                    thread.setContextClassLoader(previousLoader);
                }
            }
            updateHeader();
        }

        @Override
        public void onAlertRequested(Alert alert) {
            if (owner == null) return;
            AlertDialog dialog = alert.prepareDialog(getContext());
            dialog.show();
            if (alert.finiteTimeout()) postDelayed(dialog::dismiss, alert.getTimeout());
        }
    }
}
