/*
 * Copyright 2026 OpenAI
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 */
package ru.woesss.j2me.installer;

import com.android.dx.command.dexer.Main;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.jar.JarFile;

import ru.playsoftware.j2meloader.config.Config;
import ru.playsoftware.j2meloader.util.ConverterException;
import ru.playsoftware.j2meloader.util.FileUtils;
import ru.playsoftware.j2meloader.util.ZipUtils;
import ru.woesss.j2me.jar.Descriptor;

/**
 * Converts an imported raw JAR into the exact dex/resource/descriptor layout consumed by MicroLoader.
 * Each slot gets a distinct converted-app directory and a distinct temporary directory.
 * This prepares runtime artifacts; it deliberately does not launch a MIDlet in the current global UI.
 */
public final class MultiGameRuntimePreparer {
    public static final class RuntimePackage {
        public final int slot;
        public final File appDirectory;
        public final String name;
        public final String vendor;
        public final String version;

        RuntimePackage(int slot, File appDirectory, Descriptor descriptor) {
            this.slot = slot;
            this.appDirectory = appDirectory;
            this.name = descriptor.getName();
            this.vendor = descriptor.getVendor();
            this.version = descriptor.getVersion();
        }
    }

    private MultiGameRuntimePreparer() { }

    public static RuntimePackage prepare(File sourceJar, int zeroBasedSlot)
            throws IOException, ConverterException {
        if (sourceJar == null || !sourceJar.isFile()) {
            throw new IOException("Prepared source JAR does not exist");
        }
        if (zeroBasedSlot < 0 || zeroBasedSlot > 3) {
            throw new IllegalArgumentException("slot must be 0..3");
        }

        File appRoot = new File(Config.getAppDir());
        if (!appRoot.isDirectory() && !appRoot.mkdirs()) {
            throw new IOException("Cannot create converted-app directory: " + appRoot);
        }
        String appName = "multigame-slot-" + (zeroBasedSlot + 1);
        File target = new File(appRoot, appName);
        File temp = new File(appRoot, "." + appName + ".tmp");
        File backup = new File(appRoot, "." + appName + ".bak");
        FileUtils.deleteDirectory(temp);
        FileUtils.deleteDirectory(backup);
        if (!temp.mkdirs()) throw new IOException("Cannot create temporary runtime directory");

        Descriptor descriptor;
        try {
            descriptor = readDescriptor(sourceJar);
            File dex = new File(temp, Config.MIDLET_DEX_FILE);
            Main.main(new String[]{"--no-optimize", "--core-library",
                    "--output=" + dex.getAbsolutePath(), sourceJar.getAbsolutePath()});
            if (!dex.isFile() || dex.length() == 0) {
                throw new IOException("Dex converter produced no output");
            }
            File resourceJar = new File(temp, Config.MIDLET_RES_FILE);
            FileUtils.copyFileUsingChannel(sourceJar, resourceJar);
            descriptor.writeTo(new File(temp, Config.MIDLET_MANIFEST_FILE));

            String icon = descriptor.getIcon();
            if (icon != null && !icon.trim().isEmpty()) {
                File iconFile = new File(temp, Config.MIDLET_ICON_FILE);
                try {
                    ZipUtils.unzipEntry(resourceJar, icon, iconFile);
                } catch (IOException ignored) {
                    // An optional icon must not prevent an otherwise valid game from being prepared.
                    //noinspection ResultOfMethodCallIgnored
                    iconFile.delete();
                }
            }

            if (target.exists() && !target.renameTo(backup)) {
                throw new IOException("Cannot back up previous runtime package");
            }
            if (!temp.renameTo(target)) {
                if (backup.exists()) {
                    //noinspection ResultOfMethodCallIgnored
                    backup.renameTo(target);
                }
                throw new IOException("Cannot commit converted runtime package");
            }
            FileUtils.deleteDirectory(backup);
            return new RuntimePackage(zeroBasedSlot, target, descriptor);
        } catch (ConverterException | IOException e) {
            FileUtils.deleteDirectory(temp);
            if (!target.exists() && backup.exists()) {
                //noinspection ResultOfMethodCallIgnored
                backup.renameTo(target);
            }
            throw e;
        } catch (Throwable e) {
            FileUtils.deleteDirectory(temp);
            if (!target.exists() && backup.exists()) {
                //noinspection ResultOfMethodCallIgnored
                backup.renameTo(target);
            }
            throw new ConverterException("Runtime conversion failed: " + e.getMessage(), e);
        }
    }

    private static Descriptor readDescriptor(File jarFile) throws IOException {
        try (JarFile jar = new JarFile(jarFile)) {
            java.util.jar.Manifest manifest = jar.getManifest();
            if (manifest == null) throw new IOException("JAR has no manifest");
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            try (InputStream in = jar.getInputStream(jar.getEntry(JarFile.MANIFEST_NAME))) {
                if (in == null) throw new IOException("JAR has no META-INF/MANIFEST.MF");
                byte[] buffer = new byte[8192];
                int count;
                while ((count = in.read(buffer)) != -1) bytes.write(buffer, 0, count);
            }
            return new Descriptor(new String(bytes.toByteArray(), StandardCharsets.UTF_8), false);
        } catch (IllegalArgumentException e) {
            throw new IOException("Invalid JAR manifest: " + e.getMessage(), e);
        }
    }
}
