package javax.microedition.shell;

import android.text.TextUtils;

import androidx.annotation.Keep;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import javax.microedition.lcdui.Display;

/** Session-aware {@link java.lang.System} delegate for transformed MIDlets. */
@Keep
public final class MidletSystem {
    /** Compatibility properties for the original single-game player. */
    private static final Map<String, String> LEGACY_PROPERTIES = new ConcurrentHashMap<>();
    /** Properties scoped to the MIDlet's defining ClassLoader. */
    private static final Map<ClassLoader, Map<String, String>> SESSION_PROPERTIES = new ConcurrentHashMap<>();

    private MidletSystem() { }

    static void setProperty(String key, String value) {
        if (key == null) return;
        if (value == null) LEGACY_PROPERTIES.remove(key);
        else LEGACY_PROPERTIES.put(key, value);
    }

    /** Set a property for one MIDlet without mutating another session's property view. */
    public static void setProperty(ClassLoader loader, String key, String value) {
        if (loader == null) {
            setProperty(key, value);
            return;
        }
        Map<String, String> properties = SESSION_PROPERTIES.get(loader);
        if (properties == null) {
            Map<String, String> created = new ConcurrentHashMap<>();
            Map<String, String> previous = SESSION_PROPERTIES.putIfAbsent(loader, created);
            properties = previous == null ? created : previous;
        }
        if (key == null) return;
        if (value == null) properties.remove(key);
        else properties.put(key, value);
    }

    /** Read a scoped value explicitly while preparing a session, independent of current TCCL. */
    public static String getPropertyForSession(ClassLoader loader, String key) {
        Map<String, String> properties = loader == null ? null : SESSION_PROPERTIES.get(loader);
        String value = properties == null ? null : properties.get(key);
        if (TextUtils.isEmpty(value)) value = LEGACY_PROPERTIES.get(key);
        return value;
    }

    /** Remove all scoped properties when a MIDlet runtime is released. */
    public static void releaseSession(ClassLoader loader) {
        if (loader != null) SESSION_PROPERTIES.remove(loader);
    }

    private static String getScopedProperty(String key) {
        ClassLoader loader = Thread.currentThread().getContextClassLoader();
        Map<String, String> properties = loader == null ? null : SESSION_PROPERTIES.get(loader);
        return properties == null ? null : properties.get(key);
    }

    public static String getProperty(String key) {
        String value = getScopedProperty(key);
        if (TextUtils.isEmpty(value)) value = LEGACY_PROPERTIES.get(key);
        if (Display.isMultiTouchSupported() && "com.nokia.pointer.number".equals(key)) {
            return Display.getDisplay(null).getPointerNumber();
        }
        if (TextUtils.isEmpty(value)) value = System.getProperty(key);
        return value;
    }

    public static String getProperty(String key, String def) {
        String value = getScopedProperty(key);
        if (TextUtils.isEmpty(value)) value = LEGACY_PROPERTIES.get(key);
        if (TextUtils.isEmpty(value)) value = System.getProperty(key, def);
        return value;
    }
}
