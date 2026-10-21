/*
 * Copyright 2015-2016 Nickolay Savchenko
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

package javax.microedition.shell;

import android.util.Log;

import net.lingala.zip4j.ZipFile;
import net.lingala.zip4j.exception.ZipException;
import net.lingala.zip4j.model.FileHeader;

import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;

import dalvik.system.DexClassLoader;
import ru.playsoftware.j2meloader.BuildConfig;
import ru.playsoftware.j2meloader.config.Config;
import ru.playsoftware.j2meloader.util.FileUtils;
import ru.playsoftware.j2meloader.util.IOUtils;

public class AppClassLoader extends DexClassLoader {
	private static final String TAG = AppClassLoader.class.getName();

	/** Last-created loader is retained only as a compatibility fallback for legacy callers. */
	private static volatile AppClassLoader instance;
	private static volatile String fallbackDataDir;

	// These resources belong to this MIDlet class loader, never to another game session.
	private final ZipFile zipFile;
	private final String dataDir;
	private final File oldResDir;

	AppClassLoader(String paths, String tmpDir, ClassLoader parent, File appDir) {
		super(paths, tmpDir, null, parent);
		if (appDir == null)
			throw new NullPointerException("App path is null");
		oldResDir = new File(appDir, Config.MIDLET_RES_DIR);
		File parentDir = appDir.getParentFile();
        File root = parentDir == null ? null : parentDir.getParentFile();
		if (root == null) throw new IllegalArgumentException("MIDlet app path has no data root: " + appDir);
		dataDir = root.getPath() + Config.MIDLET_DATA_DIR + appDir.getName();
		instance = this;
		File jar = new File(appDir, Config.MIDLET_RES_FILE);
		zipFile = jar.exists() ? new ZipFile(jar) : null;
	}

	public static void setDataDir(File appDir) {
		File parent = appDir.getParentFile();
		File root = parent == null ? null : parent.getParentFile();
		if (root == null) throw new IllegalArgumentException("MIDlet app path has no data root: " + appDir);
		fallbackDataDir = root.getPath() + Config.MIDLET_DATA_DIR + appDir.getName();
	}

	private static AppClassLoader loaderFor(Class<?> resClass) {
		if (resClass != null && resClass.getClassLoader() instanceof AppClassLoader) {
			return (AppClassLoader) resClass.getClassLoader();
		}
		ClassLoader context = Thread.currentThread().getContextClassLoader();
		if (context instanceof AppClassLoader) return (AppClassLoader) context;
		return instance;
	}

	public static InputStream getResourceAsStream(Class<?> resClass, String resName) {
		Log.d(TAG, "CUSTOM GET RES CALLED WITH PATH: " + resName);
		if (resName == null || resName.equals("")) {
			Log.w(TAG, "Can't load res on empty path");
			return null;
		}
		// Add support for Siemens file path
		String normName = resName.replace('\\', '/');
		// Remove double slashes
		normName = normName.replaceAll("//+", "/");
		if (normName.charAt(0) != '/' && resClass != null && resClass.getPackage() != null) {
			String className = resClass.getPackage().getName().replace('.', '/');
			normName = className + "/" + normName;
		}
		// Remove leading slash
		if (normName.charAt(0) == '/') {
			normName = normName.substring(1);
		}
		AppClassLoader loader = loaderFor(resClass);
		byte[] data = loader == null ? getParentResourceBytes(normName) : loader.getResourceBytesForSession(normName);
		if (data == null) {
			Log.w(TAG, "Can't load res: " + resName);
			return null;
		}
		return new ByteArrayInputStream(data);
	}

	public static String getDataDir() {
		AppClassLoader loader = loaderFor(null);
		if (loader != null) return loader.dataDir;
		return fallbackDataDir;
	}

	public static byte[] getResourceAsBytes(String resName) {
		if (resName == null || resName.equals("")) {
			Log.w(TAG, "Can't load res on empty path");
			return null;
		}
		// Add support for Siemens file path
		String normName = resName.replace('\\', '/');
		// Remove double slashes
		normName = normName.replaceAll("//+", "/");
		// Remove leading slash
		if (normName.charAt(0) == '/') {
			normName = normName.substring(1);
		}
		AppClassLoader loader = loaderFor(null);
		byte[] data = loader == null ? getParentResourceBytes(normName) : loader.getResourceBytesForSession(normName);
		if (data == null) {
			Log.w(TAG, "Can't load res: " + resName);
			return null;
		}
		return data;
	}

	private byte[] getResourceBytesForSession(String name) {
		if (name.equals("")) {
			Log.w(TAG, "Can't load res on empty path");
			return null;
		}
		if (!BuildConfig.FULL_EMULATOR) {
			try {
				InputStream stream = AppClassLoader.class.getClassLoader().getResourceAsStream(name);
				if (stream != null) {
					return IOUtils.toByteArray(stream);
				}
				Log.e(TAG, "getResourceBytes: resource not found " + name);
				return null;
			} catch (Exception e) {
				Log.e(TAG, "getResourceBytes: " + name, e);
				return null;
			}
		}
		if (zipFile == null) {
			final File file = new File(oldResDir, name);
			try {
				return FileUtils.getBytes(file);
			} catch (Exception e) {
				Log.w(TAG, "getResourceBytes: from file=" + file, e);
				return null;
			}
		}
		DataInputStream dis = null;
		try {
			FileHeader header = zipFile.getFileHeader(name);
			if (header == null) {
				return null;
			}
			dis = new DataInputStream(zipFile.getInputStream(header));
			byte[] data = new byte[(int) header.getUncompressedSize()];
			dis.readFully(data);
			return data;
		} catch (Exception e) {
			Log.e(TAG, "getResourceBytes: ", e);
		} finally {
			if (dis != null) {
				try {
					dis.close();
				} catch (IOException e) {
					Log.e(TAG, "getResourceBytes: error close stream", e);
				}
			}
		}
		return null;
	}

	private static byte[] getParentResourceBytes(String name) {
		try (InputStream stream = AppClassLoader.class.getClassLoader().getResourceAsStream(name)) {
			return stream == null ? null : IOUtils.toByteArray(stream);
		} catch (Exception e) {
			Log.e(TAG, "getParentResourceBytes: " + name, e);
			return null;
		}
	}

	public static AppClassLoader getInstance() {
		return instance;
	}

	@Override
	protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
		// First, check if the class is already loaded
		Class<?> loadedClass = findLoadedClass(name);
		if (loadedClass != null) {
			return loadedClass;
		}

		try {
			// Try to load the class from the URLs of this classloader
			Class<?> localClass = findClass(name);
			if (resolve) {
				resolveClass(localClass);
			}
			return localClass;
		} catch (ClassNotFoundException e) {
			// Class not found in this classloader, delegate to parent classloader
			return super.loadClass(name, resolve);
		}
	}
}
