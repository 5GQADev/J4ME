/*
 * Copyright 2026 OpenAI
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */
package javax.microedition.shell.multigame;

import android.content.ContentResolver;
import android.net.Uri;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.jar.Attributes;
import java.util.jar.Manifest;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/** Copies and validates a selected JAR into app-private storage for one multi-game slot. */
public final class MultiGamePackageStore {
    private MultiGamePackageStore() { }

    public static final class PreparedPackage {
        public final File jarFile;
        public final String midletName;
        public final String midletClass;
        public final String vendor;
        public final String version;
        public final List<MidletEntry> midlets;

        PreparedPackage(File jarFile, String midletName, String midletClass,
                        String vendor, String version, List<MidletEntry> midlets) {
            this.jarFile = jarFile;
            this.midletName = midletName;
            this.midletClass = midletClass;
            this.vendor = vendor;
            this.version = version;
            this.midlets = Collections.unmodifiableList(new ArrayList<>(midlets));
        }
    }

    public static final class MidletEntry {
        public final String title;
        public final String className;

        MidletEntry(String title, String className) {
            this.title = title;
            this.className = className;
        }

        @Override public String toString() { return title + " · " + className; }
    }

    /**
     * Import one JAR atomically. The existing prepared JAR is preserved if copy or validation fails.
     * slot is zero-based.
     */
    public static PreparedPackage importJar(ContentResolver resolver, Uri source,
                                            File filesDir, int slot) throws IOException {
        if (resolver == null || source == null || filesDir == null) {
            throw new IllegalArgumentException("resolver, source and filesDir are required");
        }
        if (slot < 0 || slot > 3) throw new IllegalArgumentException("slot must be 0..3");

        File slotDir = new File(new File(filesDir, "multigame"), "slot-" + (slot + 1));
        if (!slotDir.isDirectory() && !slotDir.mkdirs()) {
            throw new IOException("Cannot create private slot directory: " + slotDir);
        }
        File temp = new File(slotDir, "game.jar.tmp");
        File target = new File(slotDir, "game.jar");
        if (temp.exists() && !temp.delete()) throw new IOException("Cannot clear temporary JAR");

        try (InputStream in = resolver.openInputStream(source)) {
            if (in == null) throw new IOException("Cannot open selected document");
            try (FileOutputStream out = new FileOutputStream(temp)) {
                byte[] buffer = new byte[16 * 1024];
                int count;
                while ((count = in.read(buffer)) != -1) out.write(buffer, 0, count);
                out.getFD().sync();
            }
        } catch (IOException e) {
            // Keep the previous prepared file intact.
            //noinspection ResultOfMethodCallIgnored
            temp.delete();
            throw e;
        }

        PreparedPackage parsed;
        try {
            parsed = inspectJar(temp, target);
        } catch (IOException e) {
            //noinspection ResultOfMethodCallIgnored
            temp.delete();
            throw e;
        }
        File backup = new File(slotDir, "game.jar.bak");
        if (backup.exists() && !backup.delete()) {
            //noinspection ResultOfMethodCallIgnored
            temp.delete();
            throw new IOException("Cannot clear previous backup");
        }
        boolean hadTarget = target.exists();
        if (hadTarget && !target.renameTo(backup)) {
            //noinspection ResultOfMethodCallIgnored
            temp.delete();
            throw new IOException("Cannot back up previous prepared JAR");
        }
        if (!temp.renameTo(target)) {
            if (hadTarget) {
                //noinspection ResultOfMethodCallIgnored
                backup.renameTo(target);
            }
            //noinspection ResultOfMethodCallIgnored
            temp.delete();
            throw new IOException("Cannot commit imported JAR");
        }
        if (backup.exists()) {
            //noinspection ResultOfMethodCallIgnored
            backup.delete();
        }
        return new PreparedPackage(target, parsed.midletName, parsed.midletClass,
                parsed.vendor, parsed.version, parsed.midlets);
    }

    private static PreparedPackage inspectJar(File candidate, File finalPath) throws IOException {
        try (ZipFile zip = new ZipFile(candidate)) {
            ZipEntry manifestEntry = zip.getEntry("META-INF/MANIFEST.MF");
            if (manifestEntry == null) throw new IOException("JAR has no META-INF/MANIFEST.MF");
            Manifest manifest;
            try (InputStream input = zip.getInputStream(manifestEntry)) {
                manifest = new Manifest(input);
            }
            Attributes attrs = manifest.getMainAttributes();
            List<MidletEntry> midlets = new ArrayList<>();
            for (int index = 1; ; index++) {
                String entry = trim(attrs.getValue("MIDlet-" + index));
                if (entry == null) break;
                String[] fields = entry.split(",", 3);
                if (fields.length != 3 || fields[0].trim().isEmpty() || fields[2].trim().isEmpty()) {
                    throw new IOException("Invalid MIDlet-" + index + " entry in manifest");
                }
                String title = fields[0].trim();
                String clazz = fields[2].trim();
                if (zip.getEntry(clazz.replace('.', '/') + ".class") == null) {
                    throw new IOException("MIDlet class not found in JAR: " + clazz);
                }
                midlets.add(new MidletEntry(title, clazz));
            }
            if (midlets.isEmpty()) throw new IOException("Manifest has no MIDlet-1 entry");
            MidletEntry first = midlets.get(0);
            return new PreparedPackage(finalPath, first.title, first.className,
                    trim(attrs.getValue("MIDlet-Vendor")), trim(attrs.getValue("MIDlet-Version")), midlets);
        } catch (IOException e) {
            throw e;
        } catch (Exception e) {
            throw new IOException("Invalid JAR: " + e.getMessage(), e);
        }
    }

    private static String trim(String value) {
        if (value == null) return null;
        String result = value.trim();
        return result.isEmpty() ? null : result;
    }
}
