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

package javax.microedition.util;

import android.app.Application;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Process;
import android.os.Vibrator;
import android.view.Display;
import android.view.WindowManager;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.ref.WeakReference;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Objects;

import javax.microedition.lcdui.keyboard.VirtualKeyboard;
import javax.microedition.lcdui.overlay.OverlayView;
import javax.microedition.shell.AppClassLoader;
import javax.microedition.shell.MicroActivity;

import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import ru.playsoftware.j2meloader.BuildConfig;
import ru.playsoftware.j2meloader.config.Config;

public class ContextHolder {
	private static Display display;
	private static VirtualKeyboard vk;
	private static WeakReference<MicroActivity> currentActivity;
	private static Vibrator vibrator;
	private static Context appContext;
	private static final ArrayList<ActivityResultListener> resultListeners = new ArrayList<>();
	private static boolean vibrationEnabled;

	/* Per-MIDlet UI bindings. Legacy single-player fields above remain as fallback. */
	private static final Map<ClassLoader, WeakReference<Context>> sessionContexts = new ConcurrentHashMap<>();
	private static final Map<ClassLoader, KeyboardBinding> sessionKeyboards = new ConcurrentHashMap<>();
	private static final Map<ClassLoader, WeakReference<OverlayView>> sessionOverlays = new ConcurrentHashMap<>();

	private static final class KeyboardBinding {
		final VirtualKeyboard keyboard;
		KeyboardBinding(VirtualKeyboard keyboard) { this.keyboard = keyboard; }
	}

	public static Context getAppContext() {
		return appContext;
	}

	public static VirtualKeyboard getVk() {
		ClassLoader loader = Thread.currentThread().getContextClassLoader();
		KeyboardBinding binding = loader == null ? null : sessionKeyboards.get(loader);
		return binding == null ? vk : binding.keyboard;
	}

	/** Legacy single-player setter. Multi-session hosts should use setSessionVk. */
	public static void setVk(VirtualKeyboard vk) {
		ContextHolder.vk = vk;
	}

	/** Bind a host Context to one MIDlet classloader without changing the legacy Activity. */
	public static void bindSessionContext(ClassLoader loader, Context context) {
		if (loader == null || context == null) throw new NullPointerException();
		sessionContexts.put(loader, new WeakReference<>(context));
	}

	/** Return the Context registered for a session, or null if that session has no host. */
	public static Context getSessionContext(ClassLoader loader) {
		if (loader == null) return null;
		WeakReference<Context> ref = sessionContexts.get(loader);
		Context context = ref == null ? null : ref.get();
		if (ref != null && context == null) sessionContexts.remove(loader, ref);
		return context;
	}

	/** Resolve the current session Context using the calling thread's context classloader. */
	public static Context getSessionContext() {
		ClassLoader loader = Thread.currentThread().getContextClassLoader();
		Context context = getSessionContext(loader);
		if (context != null) return context;
		MicroActivity activity = getActivity();
		return activity != null ? activity : appContext;
	}

	/** Bind an overlay surface owned by a specific game slot. */
	public static void setSessionOverlay(ClassLoader loader, OverlayView overlay) {
		if (loader == null) throw new NullPointerException("loader");
		if (overlay == null) sessionOverlays.remove(loader);
		else sessionOverlays.put(loader, new WeakReference<>(overlay));
	}

	/** Resolve the overlay for the current MIDlet, falling back to the legacy player overlay. */
	public static OverlayView getSessionOverlay() {
		ClassLoader loader = Thread.currentThread().getContextClassLoader();
		WeakReference<OverlayView> ref = loader == null ? null : sessionOverlays.get(loader);
		OverlayView overlay = ref == null ? null : ref.get();
		if (ref != null && overlay == null) sessionOverlays.remove(loader, ref);
		if (overlay != null) return overlay;
		MicroActivity activity = getActivity();
		return activity == null ? null : activity.binding.overlayView;
	}

	/** Set an explicit keyboard binding; a null keyboard means disabled for that session. */
	public static void setSessionVk(ClassLoader loader, VirtualKeyboard keyboard) {
		if (loader == null) throw new NullPointerException("loader");
		sessionKeyboards.put(loader, new KeyboardBinding(keyboard));
	}

	/** Remove all ContextHolder state owned by a finished MIDlet session. */
	public static void releaseSession(ClassLoader loader) {
		if (loader == null) return;
		sessionContexts.remove(loader);
		sessionKeyboards.remove(loader);
		sessionOverlays.remove(loader);
	}

	private static Display getDisplay() {
		if (display == null) {
			display = ((WindowManager) Objects.requireNonNull(getAppContext().getSystemService(Context.WINDOW_SERVICE))).getDefaultDisplay();
		}
		return display;
	}

	public static int getDisplayWidth() {
		return getDisplay().getWidth();
	}

	public static int getDisplayHeight() {
		return getDisplay().getHeight();
	}

	public static void setCurrentActivity(MicroActivity activity) {
		currentActivity = new WeakReference<>(activity);
	}

	public static void addActivityResultListener(ActivityResultListener listener) {
		if (!resultListeners.contains(listener)) {
			resultListeners.add(listener);
		}
	}

	public static void removeActivityResultListener(ActivityResultListener listener) {
		resultListeners.remove(listener);
	}

	public static void notifyOnActivityResult(int requestCode, int resultCode, Intent data) {
		for (ActivityResultListener listener : resultListeners) {
			listener.onActivityResult(requestCode, resultCode, data);
		}
	}

	public static InputStream getResourceAsStream(Class<?> resClass, String resName) {
		return AppClassLoader.getResourceAsStream(resClass, resName);
	}

	public static FileOutputStream openFileOutput(String name) throws FileNotFoundException {
		File dir = new File(AppClassLoader.getDataDir());
		File file = new File(dir, name);
		if (!dir.isDirectory() && !dir.mkdirs()) {
			throw new FileNotFoundException("Can't create directory: " + dir);
		}
		return new FileOutputStream(file);
	}

	public static FileInputStream openFileInput(String name) throws FileNotFoundException {
		return new FileInputStream(getFileByName(name));
	}

	public static boolean deleteFile(String name) {
		return getFileByName(name).delete();
	}

	public static File getFileByName(String name) {
		return new File(AppClassLoader.getDataDir(), name);
	}

	public static File getCacheDir() {
		if (BuildConfig.FULL_EMULATOR) {
			File dir = new File(Config.getEmulatorDir() + "/cache");
			if (dir.isDirectory() || dir.mkdirs())
				return dir;
		}
		return getAppContext().getCacheDir();
	}

	public static boolean requestPermission(String permission) {
		MicroActivity context = getActivity();
		if (context == null) {
			return false;
		}
		if (ContextCompat.checkSelfPermission(context, permission) != PackageManager.PERMISSION_GRANTED) {
			ActivityCompat.requestPermissions(context, new String[]{permission}, 0);
			return false;
		} else {
			return true;
		}
	}

	public static boolean requestPermissions(String[] permissions) {
		MicroActivity context = getActivity();
		if (context == null) {
			return false;
		}
		if (!hasPermissions(context, permissions)) {
			ActivityCompat.requestPermissions(context, permissions, 0);
			return false;
		} else {
			return true;
		}
	}

	private static boolean hasPermissions(Context context, String... permissions) {
		if (context != null && permissions != null) {
			for (String permission : permissions) {
				if (ActivityCompat.checkSelfPermission(context, permission) != PackageManager.PERMISSION_GRANTED) {
					return false;
				}
			}
		}
		return true;
	}

	public static String getAssetAsString(String fileName) {
		StringBuilder sb = new StringBuilder();

		//noinspection CharsetObjectCanBeUsed
		try (InputStream is = getAppContext().getAssets().open(fileName);
			 BufferedReader br = new BufferedReader(new InputStreamReader(is, Charset.forName("UTF-8")))) {
			String str;
			while ((str = br.readLine()) != null) {
				sb.append(str).append('\n');
			}
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
		return sb.toString();
	}

	public static MicroActivity getActivity() {
		WeakReference<MicroActivity> reference = currentActivity;
		return reference == null ? null : reference.get();
	}

	public static boolean vibrate(int duration) {
		if (!vibrationEnabled) {
			return false;
		}
		if (vibrator == null) {
			vibrator = (Vibrator) getAppContext().getSystemService(Context.VIBRATOR_SERVICE);
		}
		if (vibrator == null || !vibrator.hasVibrator()) {
			return false;
		}
		if (duration > 0) {
			vibrator.vibrate(duration);
		} else if (duration < 0) {
			throw new IllegalStateException();
		} else {
			vibrator.cancel();
		}
		return true;
	}

	public static void vibrateKey(int duration) {
		if (vibrator == null) {
			vibrator = (Vibrator) getAppContext().getSystemService(Context.VIBRATOR_SERVICE);
		}
		if (vibrator == null || !vibrator.hasVibrator()) {
			return;
		}
		vibrator.vibrate(duration);
	}

	public static void setApplication(Application application) {
		appContext = application;
	}

	public static void setVibration(boolean vibrationEnabled) {
		ContextHolder.vibrationEnabled = vibrationEnabled;
	}
}
