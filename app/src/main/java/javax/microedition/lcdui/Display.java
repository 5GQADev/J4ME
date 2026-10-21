/*
 * Copyright 2012 Kulikov Dmitriy
 * Copyright 2017-2018 Nikita Shakarun
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

package javax.microedition.lcdui;

import androidx.appcompat.app.AlertDialog;

import javax.microedition.lcdui.event.Event;
import javax.microedition.lcdui.event.EventQueue;
import javax.microedition.lcdui.event.RunnableEvent;
import javax.microedition.midlet.MIDlet;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import javax.microedition.util.ContextHolder;

import ru.woesss.j2me.jar.Descriptor;

@SuppressWarnings("unused")
public class Display {
	public static final int LIST_ELEMENT = 1;
	public static final int CHOICE_GROUP_ELEMENT = 2;
	public static final int ALERT = 3;

	public static final int COLOR_BACKGROUND = 0;
	public static final int COLOR_FOREGROUND = 1;
	public static final int COLOR_HIGHLIGHTED_BACKGROUND = 2;
	public static final int COLOR_HIGHLIGHTED_FOREGROUND = 3;
	public static final int COLOR_BORDER = 4;
	public static final int COLOR_HIGHLIGHTED_BORDER = 5;

	private static final int[] COLORS =
			{
					0xFFD0D0D0,
					0xFF000080,
					0xFF000080,
					0xFFFFFFFF,
					0xFFFFFFFF,
					0xFF000080
			};

	/*
	 * MIDP specifies one Display per MIDlet. The old implementation used a single process-wide
	 * instance and event queue, causing one game's callSerially/repaint events to be delivered
	 * through another game's queue. Keep a legacy default for existing host/UI code, but maintain
	 * a separate Display and queue for each MIDlet classloader.
	 */
	private static final Map<ClassLoader, Display> DISPLAYS = new ConcurrentHashMap<>();
	private static final Map<ClassLoader, SessionViewHost> SESSION_HOSTS = new ConcurrentHashMap<>();
	private static volatile Display instance; // legacy/default display for host code without a game context
	private final ClassLoader ownerLoader;
	private final EventQueue queue = new EventQueue();
	private boolean multiTouchSupported;
	private String pointerNumber;
	private Displayable current;

	/** Host contract used to route a MIDlet's display changes to its own view container. */
	public interface SessionViewHost {
		void onDisplayableChanged(Displayable previous, Displayable next);
		void onAlertRequested(Alert alert);
		/** Called on the main thread after a session MIDlet has been constructed. */
		default void onSessionCreated(MIDlet midlet) { }
		/** Called when this MIDlet session is being released. */
		default void onSessionDestroyed(MIDlet midlet) { }
		/** Called on the main thread when session initialization fails. */
		default void onSessionFailed(Throwable error) { }
	}

	private Display(ClassLoader ownerLoader) {
		this.ownerLoader = ownerLoader;
		queue.startProcessing();
	}

	private static ClassLoader keyFor(MIDlet midlet) {
		ClassLoader loader = midlet == null ? null : midlet.getClass().getClassLoader();
		return loader == null ? Display.class.getClassLoader() : loader;
	}

	private static Display forLoader(ClassLoader loader) {
		if (loader != null) {
			Display display = DISPLAYS.get(loader);
			if (display != null) return display;
		}
		return instance;
	}

	public static Display getDisplay(MIDlet midlet) {
		if (midlet == null) return instance;
		ClassLoader loader = keyFor(midlet);
		return DISPLAYS.computeIfAbsent(loader, ignored -> {
			Display display = new Display(loader);
			String enhancement = midlet.getAppProperty(Descriptor.NOKIA_UI_ENHANCEMENT);
			if (enhancement != null) {
				display.multiTouchSupported = enhancement.contains("EnableMultiPointTouchEvents");
			}
			if (instance == null) instance = display;
			return display;
		});
	}

	/** Reset the legacy/default display when the single-game host starts a new MIDlet. */
	public static void initDisplay() {
		Display old = instance;
		instance = null;
		// A mapped Display belongs to a MIDlet session; never stop it merely because another
		// MicroLoader is initializing. Its owner releases it through releaseDisplay(midlet).
		if (old != null && !DISPLAYS.containsValue(old)) old.queue.stopProcessing();
	}

	/** Release one MIDlet's Display and event queue without disturbing other sessions. */
	public static void releaseDisplay(MIDlet midlet) {
		if (midlet == null) return;
		ClassLoader loader = keyFor(midlet);
		Display removed = DISPLAYS.remove(loader);
		SessionViewHost host = SESSION_HOSTS.remove(loader);
		if (host != null) ViewHandler.postEvent(() -> host.onSessionDestroyed(midlet));
		javax.microedition.util.ContextHolder.releaseSession(loader);
		stopDisplay(removed);
	}

	/** Release a loader whose MIDlet constructor failed before an instance could be returned. */
	public static void releaseDisplay(ClassLoader loader) {
		if (loader == null) return;
		Display removed = DISPLAYS.remove(loader);
		SESSION_HOSTS.remove(loader);
		javax.microedition.util.ContextHolder.releaseSession(loader);
		stopDisplay(removed);
	}

	private static void stopDisplay(Display removed) {
		if (removed != null) {
			removed.queue.stopProcessing();
			if (instance == removed) instance = null;
		}
	}

	public static void postEvent(Event event) {
		getEventQueue().postEvent(event);
	}

	/** Explicit event routing for host code that dispatches input on a non-game UI thread. */
	public static void postEvent(MIDlet midlet, Event event) {
		Display display = midlet == null ? instance : getDisplay(midlet);
		if (display != null) display.queue.postEvent(event);
	}

	static EventQueue getEventQueue() {
		ClassLoader context = Thread.currentThread().getContextClassLoader();
		Display display = forLoader(context);
		if (display == null) {
			display = new Display(null);
			instance = display;
		}
		return display.queue;
	}

	public static boolean isMultiTouchSupported() {
		ClassLoader context = Thread.currentThread().getContextClassLoader();
		Display display = forLoader(context);
		return display != null && display.multiTouchSupported;
	}

	static void setPointerNumber(int pointerNumber) {
		Display display = currentDisplay();
		if (display != null) display.pointerNumber = String.valueOf(pointerNumber);
	}

	static void resetPointerNumber() {
		Display display = currentDisplay();
		if (display != null) display.pointerNumber = null;
	}

	public static String getPointerNumber() {
		Display display = currentDisplay();
		return display == null ? null : display.pointerNumber;
	}

	private static Display currentDisplay() {
		return forLoader(Thread.currentThread().getContextClassLoader());
	}

	public void setCurrent(Displayable disp) {
		if (disp == current) {
			return;
		}
		Displayable previous = current;
		if (current instanceof Canvas) {
			Canvas c = (Canvas) current;
			c.setInvisible();
		}
		if (disp instanceof Alert) {
			Alert alert = (Alert) disp;
			alert.setNextDisplayable(current);
			showAlert(alert);
		} else {
			current = disp;
			showCurrent(previous, disp);
		}
	}

	public void setCurrent(final Alert alert, Displayable disp) {
		if (disp == null) {
			throw new NullPointerException();
		} else if (disp instanceof Alert) {
			throw new IllegalArgumentException();
		}
		alert.setNextDisplayable(disp);
		showAlert(alert);
	}

	private void showAlert(Alert alert) {
		SessionViewHost host = ownerLoader == null ? null : SESSION_HOSTS.get(ownerLoader);
		if (host != null) {
			ViewHandler.postEvent(() -> {
				// A queued alert belongs to the host registration that existed when it was
				// requested. Do not show it after that session has been detached or replaced.
				if (SESSION_HOSTS.get(ownerLoader) == host) host.onAlertRequested(alert);
			});
			return;
		}
		ViewHandler.postEvent(() -> {
			AlertDialog alertDialog = alert.prepareDialog();
			alertDialog.show();
			if (alert.finiteTimeout()) {
				ViewHandler.postDelayed(alertDialog::dismiss, alert.getTimeout());
			}
		});
	}

	private void showCurrent(Displayable previous, Displayable next) {
		SessionViewHost host = ownerLoader == null ? null : SESSION_HOSTS.get(ownerLoader);
		if (host != null) {
			ViewHandler.postEvent(() -> {
				// Re-check registration so a queued callback cannot target a detached host.
				if (SESSION_HOSTS.get(ownerLoader) == host) host.onDisplayableChanged(previous, next);
			});
			return;
		}
		// Preserve the original single-game behavior until a session-specific host is attached.
		if (ContextHolder.getActivity() != null) {
			ContextHolder.getActivity().setCurrent(next);
		}
	}

	/** Attach a view host to one MIDlet session. Host callbacks are invoked on the Android UI thread. */
	public static void registerSessionHost(MIDlet midlet, SessionViewHost host) {
		if (midlet == null || host == null) throw new NullPointerException();
		registerSessionHost(keyFor(midlet), host);
	}

	/** Register a host before MIDlet construction so constructor-time display changes stay in-slot. */
	public static void registerSessionHost(ClassLoader loader, SessionViewHost host) {
		if (loader == null || host == null) throw new NullPointerException();
		SESSION_HOSTS.put(loader, host);
		Display display = DISPLAYS.get(loader);
		if (display != null && display.current != null) {
			Displayable current = display.current;
			ViewHandler.postEvent(() -> {
				// Registration may have been replaced before this initial sync runs.
				if (SESSION_HOSTS.get(loader) == host) host.onDisplayableChanged(null, current);
			});
		}
	}

	/** Detach only the host associated with this MIDlet session. */
	public static void unregisterSessionHost(MIDlet midlet, SessionViewHost host) {
		if (midlet == null || host == null) return;
		SESSION_HOSTS.remove(keyFor(midlet), host);
	}

	/** Detach a host from all sessions it owns, for example when its Activity is destroyed. */
	public static void unregisterSessionHost(SessionViewHost host) {
		if (host == null) return;
		SESSION_HOSTS.entrySet().removeIf(entry -> entry.getValue() == host);
	}

	public Displayable getCurrent() {
		return current;
	}

	public void callSerially(Runnable r) {
		postEvent(RunnableEvent.getInstance(r));
	}

	public boolean flashBacklight(int duration) {
		return false;
	}

	/**
	 * @since MIDP 2.0
	 */
	public boolean vibrate(int duration) {
		return ContextHolder.vibrate(duration);
	}

	public void setCurrentItem(Item item) {
		if (item.hasOwnerForm()) {
			setCurrent(item.getOwnerForm());
		}
	}

	public int numAlphaLevels() {
		return 256;
	}

	public int numColors() {
		return Integer.MAX_VALUE;
	}

	public int getBestImageHeight(int imageType) {
		return 0;
	}

	public int getBestImageWidth(int imageType) {
		return 0;
	}

	public int getBorderStyle(boolean highlighted) {
		return highlighted ? Graphics.SOLID : Graphics.DOTTED;
	}

	public int getColor(int colorSpecifier) {
		return COLORS[colorSpecifier];
	}

	public boolean isColor() {
		return true;
	}
}
