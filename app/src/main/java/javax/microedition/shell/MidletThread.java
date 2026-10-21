/*
 *  Copyright 2020 Yury Kharchenko
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

package javax.microedition.shell;

import android.os.Handler;
import android.os.HandlerThread;
import android.os.Message;
import android.util.Log;

import javax.microedition.lcdui.Canvas;
import javax.microedition.lcdui.Display;
import javax.microedition.lcdui.Displayable;
import javax.microedition.lcdui.ViewHandler;
import javax.microedition.midlet.MIDlet;
import javax.microedition.midlet.MIDletStateChangeException;
import javax.microedition.util.ContextHolder;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

import androidx.annotation.NonNull;

import ru.playsoftware.j2meloader.config.Config;

public class MidletThread extends HandlerThread implements Handler.Callback {
	private static final String TAG = MidletThread.class.getName();
	private static final UncaughtExceptionHandler uncaughtExceptionHandler = (t, e) ->
			Log.e(TAG, "Error in thread: \"" + t + "\" after destroy app called", e);

	private static final int INIT = 0;
	private static final int START = 1;
	private static final int PAUSE = 2;
	private static final int DESTROY = 3;
	private static final int UNINITIALIZED = 0;
	private static final int STARTED = 1;
	private static final int PAUSED = 2;
	private static final int DESTROYED = 3;
	public static String[] startAfterDestroy;
	private static volatile MidletThread instance; // compatibility fallback for the original single-game host
	private static final Map<ClassLoader, MidletThread> SESSIONS = new ConcurrentHashMap<>();
	private volatile ClassLoader sessionClassLoader;
	private final MicroLoader microLoader;
	private final String mainClass;
	private MIDlet midlet;
	private final Handler handler;
	private volatile int state;
	private volatile boolean sessionFinished;
	private volatile boolean sessionHostPaused;
	private volatile boolean sessionHostBound;
	private final AtomicBoolean sessionCleanupStarted = new AtomicBoolean(false);

	private MidletThread(MicroLoader microLoader, String mainClass) {
		super("MidletMain");
		this.microLoader = microLoader;
		this.mainClass = mainClass;
		start();
		handler = new Handler(getLooper(), this);
	}

	private void beginInitialization() {
		handler.obtainMessage(INIT).sendToTarget();
	}

	static void create(MicroLoader microLoader, String mainClass) {
		MidletThread thread = new MidletThread(microLoader, mainClass);
		instance = thread;
		thread.beginInitialization();
	}

	/** Create and register an additional runtime before its INIT message can run. */
	static MidletThread createSession(MicroLoader microLoader, String mainClass) {
		MidletThread thread = new MidletThread(microLoader, mainClass);
		MultiGameSessionLauncher.registerSession(microLoader.getSessionViewHost(), thread);
		thread.beginInitialization();
		return thread;
	}

	/** Remember host visibility even if the MIDlet has not finished constructing yet. */
	void setSessionHostPaused(boolean paused) {
		if (!microLoader.isSessionMode() || sessionFinished) return;
		sessionHostPaused = paused;
		handler.obtainMessage(paused ? PAUSE : START).sendToTarget();
	}

	void markSessionHostBound() {
		sessionHostBound = true;
	}

	/** Stop this runtime even if INIT has not produced a MIDlet instance yet. */
	void requestSessionStop() {
		if (!sessionFinished) requestDestroy(this);
	}

	boolean isSessionFinished() {
		return sessionFinished;
	}

	private static MidletThread findFor(MIDlet owner) {
		// An explicit owner must never fall through to another game's legacy singleton.
		// Session threads are registered before INIT is queued.
		if (owner != null) return SESSIONS.get(owner.getClass().getClassLoader());
		return instance;
	}

	/** Legacy entry point used by the original single-game host. */
	public static void notifyDestroyed() {
		requestDestroy(findFor(null));
	}

	/** Session-aware MIDlet completion; does not terminate the Android process. */
	public static void notifyDestroyed(MIDlet owner) {
		requestDestroy(findFor(owner));
	}

	private static void requestDestroy(MidletThread thread) {
		if (thread == null || thread.state == DESTROYED) return;
		thread.handler.obtainMessage(DESTROY).sendToTarget();
	}

	public static void notifyPaused() {
		MidletThread thread = findFor(null);
		if (thread != null) thread.state = PAUSED;
	}

	public static void notifyPaused(MIDlet owner) {
		MidletThread thread = findFor(owner);
		if (thread != null) thread.state = PAUSED;
	}

	static void pauseApp() {
		MidletThread thread = findFor(null);
		if (thread != null) thread.handler.obtainMessage(PAUSE).sendToTarget();
	}

	/** Pause only the supplied MIDlet session. */
	public static void pauseApp(MIDlet owner) {
		MidletThread thread = findFor(owner);
		if (thread != null) thread.handler.obtainMessage(PAUSE).sendToTarget();
	}

	public static void resumeApp() {
		resumeApp(null);
	}

	public static void resumeApp(MIDlet owner) {
		MidletThread thread = findFor(owner);
		if (thread == null) return;
		if (owner != null) {
			// A grid-hosted session has no MicroActivity; its bound Context is the host signal.
			if (ContextHolder.getSessionContext(owner.getClass().getClassLoader()) != null) {
				thread.handler.obtainMessage(START).sendToTarget();
			}
			return;
		}
		MicroActivity activity = ContextHolder.getActivity();
		if (activity != null && activity.isVisible()) thread.handler.obtainMessage(START).sendToTarget();
	}

	static void destroyApp() {
		Thread.setDefaultUncaughtExceptionHandler(uncaughtExceptionHandler);
		MicroActivity activity = ContextHolder.getActivity();
		if (activity != null) {
			Displayable current = activity.getCurrent();
			if (current instanceof Canvas) {
				Canvas canvas = (Canvas) current;
				canvas.postKeyPressed(Canvas.KEY_END);
				canvas.postKeyReleased(Canvas.KEY_END);
			}
		}
		requestDestroy(findFor(null));
	}

	/** Destroy only the supplied MIDlet session, without consulting the legacy Activity. */
	public static void destroyApp(MIDlet owner) {
		if (owner == null) {
			destroyApp();
			return;
		}
		MidletThread thread = findFor(owner);
		if (thread == null) return;
		Displayable current = Display.getDisplay(owner).getCurrent();
		if (current instanceof Canvas) {
			Canvas canvas = (Canvas) current;
			canvas.postKeyPressed(Canvas.KEY_END);
			canvas.postKeyReleased(Canvas.KEY_END);
		}
		requestDestroy(thread);
	}

	private void cleanupSession() {
		sessionFinished = true;
		if (!sessionCleanupStarted.compareAndSet(false, true)) return;
		MultiGameSessionLauncher.releaseSession(microLoader.getSessionViewHost(), this);
		Display.releaseDisplay(midlet);
		if (sessionClassLoader != null) {
			SESSIONS.remove(sessionClassLoader, this);
			MidletSystem.releaseSession(sessionClassLoader);
			SessionCanvasConfig.release(sessionClassLoader);
			javax.microedition.lcdui.keyboard.KeyMapper.releaseSession(sessionClassLoader);
			MIDlet.releaseProps(sessionClassLoader);
			ContextHolder.releaseSession(sessionClassLoader);
		}
		microLoader.releaseSessionBindings();
	}

	private void finishSession() {
		// Grid-hosted sessions must never finish a legacy MicroActivity belonging to another game.
		MicroActivity activity = ContextHolder.getActivity();
		if (!microLoader.isSessionMode() && SESSIONS.isEmpty() && activity != null) activity.finish();
		if (!microLoader.isSessionMode() && startAfterDestroy != null && activity != null && SESSIONS.isEmpty()) {
			String[] next = startAfterDestroy;
			startAfterDestroy = null;
			Config.startApp(activity, next[0], next[1], false, next[2]);
		}
	}

	@Override
	public boolean handleMessage(@NonNull Message msg) {
		switch (msg.what) {
			case INIT:
				if (state != UNINITIALIZED) {
					break;
				}
				try {
					midlet = microLoader.loadMIDlet(this.mainClass);
					sessionClassLoader = midlet.getClass().getClassLoader();
					if (sessionClassLoader != null) {
						SESSIONS.put(sessionClassLoader, this);
						setContextClassLoader(sessionClassLoader);
						microLoader.applySessionProperties(sessionClassLoader);
					}
					state = PAUSED;
					if (microLoader.isSessionMode()) {
						// Bind the host on Android's main thread before startApp can call Display.setCurrent().
						ViewHandler.postEvent(() -> {
							// A Stop/Activity teardown may have won the race while INIT was loading.
							if (state == DESTROYED || sessionFinished) return;
							try {
								microLoader.attachSessionOverlay();
								microLoader.getSessionViewHost().onSessionCreated(midlet);
								markSessionHostBound();
								handler.obtainMessage(START).sendToTarget();
							} catch (Throwable error) {
								microLoader.getSessionViewHost().onSessionFailed(error);
								requestDestroy(MidletThread.this);
							}
						});
					}
				} catch (Throwable t) {
					state = DESTROYED;
					if (microLoader.isSessionMode()) {
						cleanupSession();
						Display.SessionViewHost host = microLoader.getSessionViewHost();
						ViewHandler.postEvent(() -> host.onSessionFailed(t));
						quitSafely();
					} else {
						throw new RuntimeException("Init midlet failed", t);
					}
				}
				break;
			case START:
				// Never start a grid MIDlet before its slot host is bound, or while
				// Android has moved the host Activity to the background.
				if (microLoader.isSessionMode() && (!sessionHostBound || sessionHostPaused)) {
					break;
				}
				if (state != PAUSED) {
					break;
				}
				try {
					state = STARTED;
					midlet.startApp();
				} catch (MIDletStateChangeException e) {
					state = PAUSED;
					Log.w(TAG, "Midlet doesn't want to start!", e);
				} catch (Throwable t) {
					state = DESTROYED;
					cleanupSession();
					finishSession();
					if (microLoader.isSessionMode()) {
						ViewHandler.postEvent(() -> microLoader.getSessionViewHost().onSessionFailed(t));
						quitSafely();
					} else {
						throw new RuntimeException("Failed startApp", t);
					}
				}
				break;
			case PAUSE:
				// sessionHostPaused is updated before this message is queued. If INIT is
				// still running, the flag survives and the later START will be gated.
				if (state != STARTED) {
					break;
				}
				try {
					midlet.pauseApp();
					state = PAUSED;
				} catch (Throwable t) {
					state = DESTROYED;
					try {
						midlet.destroyApp(true);
					} catch (MIDletStateChangeException ignored) {}
					cleanupSession();
					finishSession();
					if (microLoader.isSessionMode()) {
						ViewHandler.postEvent(() -> microLoader.getSessionViewHost().onSessionFailed(t));
						quitSafely();
					} else {
						throw new RuntimeException("Failed pauseApp", t);
					}
				}
				break;
			case DESTROY:
				if (state == DESTROYED) break;
				state = DESTROYED;
				try {
					if (midlet != null) midlet.destroyApp(true);
				} catch (MIDletStateChangeException e) {
					Log.w(TAG, "Midlet didn't want to die!", e);
				} catch (Throwable t) {
					Log.e(TAG, "Filed destroyApp:", t);
				} finally {
					// Release only this session's runtime resources.
					cleanupSession();
				}
				finishSession();
				quitSafely();
				break;
		}
		return true;
	}
}
