package javax.microedition.shell.multigame;

import android.content.Intent;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.provider.OpenableColumns;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.io.IOException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import ru.playsoftware.j2meloader.config.Config;
import ru.playsoftware.j2meloader.config.ProfileModel;
import ru.playsoftware.j2meloader.config.ProfilesManager;
import ru.playsoftware.j2meloader.util.ConverterException;
import ru.woesss.j2me.installer.MultiGameRuntimePreparer;
import javax.microedition.midlet.MIDlet;
import javax.microedition.shell.MultiGameSessionLauncher;

/**
 * Four-slot host for converted MIDlet packages. Each slot receives its own MicroLoader,
 * ClassLoader, Display host and MIDlet thread; some process-global J2ME services still
 * need device-level compatibility testing.
 */
public final class MultiGameActivity extends AppCompatActivity {
    private static final String PREFS = "multi_game_slots";
    private static final String[] SLOT_KEYS = {"slot0", "slot1", "slot2", "slot3"};
    private final Uri[] selectedUris = new Uri[4];
    private final String[] selectedNames = new String[4];
    private int pendingSlot = -1;
    private MultiGameGridView grid;
    private final Button[] selectButtons = new Button[4];
    private final Button[] prepareButtons = new Button[4];
    private final Button[] convertButtons = new Button[4];
    private final Button[] launchButtons = new Button[4];
    private final TextView[] slotStatus = new TextView[4];
    private final ExecutorService importerExecutor = Executors.newFixedThreadPool(2);
    private ActivityResultLauncher<String[]> picker;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        picker = registerForActivityResult(new ActivityResultContracts.OpenDocument(), uri -> {
            if (uri == null || pendingSlot < 0) return;
            int slot = pendingSlot;
            try {
                getContentResolver().takePersistableUriPermission(uri,
                        Intent.FLAG_GRANT_READ_URI_PERMISSION);
            } catch (SecurityException ignored) {
                // Some document providers do not offer persistable permissions.
            }
            selectedUris[slot] = uri;
            selectedNames[slot] = queryDisplayName(uri);
            getSharedPreferences(PREFS, MODE_PRIVATE).edit()
                    .putString(SLOT_KEYS[slot], uri.toString())
                    .remove("prepared_slot" + slot)
                    .remove("prepared_midlet" + slot)
                    .remove("prepared_class" + slot)
                    .remove("prepared_vendor" + slot)
                    .remove("prepared_version" + slot)
                    .remove("runtime_slot" + slot)
                    .remove("runtime_midlet" + slot)
                    .apply();
            grid.getSlot(slot).setSelectedGameName(selectedNames[slot]);
            selectButtons[slot].setText("Slot " + (slot + 1) + ": " + selectedNames[slot]);
            slotStatus[slot].setText("Selected; not imported yet");
            convertButtons[slot].setEnabled(false);
            launchButtons[slot].setEnabled(false);
            Toast.makeText(this, "Selected for slot " + (slot + 1) + ". Tap Prepare JAR, then Convert for runtime.", Toast.LENGTH_LONG).show();
        });

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(6), dp(6), dp(6), dp(6));
        TextView notice = new TextView(this);
        notice.setText("Multi-game · Prepare and convert a JAR, then launch it directly in its slot. Tap a slot header to maximize or restore. Thumbnail rendering is limited to 4 FPS; verify game-specific behavior on device.");
        notice.setPadding(dp(8), dp(4), dp(8), dp(8));
        root.addView(notice, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        for (int i = 0; i < 4; i++) {
            final int slot = i;
            selectButtons[i] = new Button(this);
            selectButtons[i].setAllCaps(false);
            selectButtons[i].setText("Choose game · Slot " + (i + 1));
            selectButtons[i].setOnClickListener(v -> {
                pendingSlot = slot;
                picker.launch(new String[]{"application/java-archive", "application/octet-stream", "*/*"});
            });
            root.addView(selectButtons[i], new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
            prepareButtons[i] = new Button(this);
            prepareButtons[i].setAllCaps(false);
            prepareButtons[i].setText("Prepare JAR · Slot " + (i + 1));
            prepareButtons[i].setOnClickListener(v -> prepareSlot(slot));
            root.addView(prepareButtons[i], new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
            convertButtons[i] = new Button(this);
            convertButtons[i].setAllCaps(false);
            convertButtons[i].setText("Convert for runtime · Slot " + (i + 1));
            convertButtons[i].setEnabled(false);
            convertButtons[i].setOnClickListener(v -> convertSlot(slot));
            root.addView(convertButtons[i], new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
            launchButtons[i] = new Button(this);
            launchButtons[i].setAllCaps(false);
            launchButtons[i].setText("Launch in grid · Slot " + (i + 1));
            launchButtons[i].setEnabled(false);
            launchButtons[i].setOnClickListener(v -> {
                MultiGameGridView.SlotHost host = grid.getSlot(slot);
                if (host.hasOwner() || MultiGameSessionLauncher.hasSessionOrLaunching(host)) {
                    host.stopSession();
                    if (!host.hasOwner()) {
                        launchButtons[slot].setText("Launch in grid · Slot " + (slot + 1));
                        launchButtons[slot].setEnabled(true);
                        slotStatus[slot].setText("Launch cancellation requested");
                    }
                } else {
                    launchRuntimeInGrid(slot);
                }
            });
            root.addView(launchButtons[i], new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
            slotStatus[i] = new TextView(this);
            slotStatus[i].setText("Not prepared");
            slotStatus[i].setPadding(dp(8), dp(1), dp(8), dp(4));
            root.addView(slotStatus[i], new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        }
        grid = new MultiGameGridView(this);
        root.addView(grid, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        setContentView(root);
        restoreSelections();
    }

    private void restoreSelections() {
        SharedPreferences prefs = getSharedPreferences(PREFS, MODE_PRIVATE);
        for (int i = 0; i < 4; i++) {
            String raw = prefs.getString(SLOT_KEYS[i], null);
            if (raw == null) continue;
            Uri uri = Uri.parse(raw);
            selectedUris[i] = uri;
            selectedNames[i] = queryDisplayName(uri);
            grid.getSlot(i).setSelectedGameName(selectedNames[i]);
            selectButtons[i].setText("Slot " + (i + 1) + ": " + selectedNames[i]);
            String prepared = prefs.getString("prepared_slot" + i, null);
            String midlet = prefs.getString("prepared_midlet" + i, null);
            String midletClass = prefs.getString("prepared_class" + i, null);
            if (prepared != null && new File(prepared).isFile()) {
                if (midletClass != null && !midletClass.trim().isEmpty()) {
                    slotStatus[i].setText("Prepared: " + (midlet == null ? selectedNames[i] : midlet));
                    convertButtons[i].setEnabled(true);
                } else {
                    slotStatus[i].setText("JAR prepared; choose a MIDlet before conversion.");
                    convertButtons[i].setEnabled(false);
                }
            }
            String runtimePath = prefs.getString("runtime_slot" + i, null);
            if (runtimePath != null && hasRuntimePackage(new File(runtimePath))) {
                slotStatus[i].setText("Runtime converted: " + (midlet == null ? selectedNames[i] : midlet));
                launchButtons[i].setEnabled(true);
            }
        }
    }

    private void prepareSlot(int slot) {
        final Uri source = selectedUris[slot];
        if (source == null) {
            Toast.makeText(this, "Choose a JAR for slot " + (slot + 1) + " first.", Toast.LENGTH_SHORT).show();
            return;
        }
        String name = selectedNames[slot] == null ? "" : selectedNames[slot].toLowerCase(java.util.Locale.ROOT);
        if (name.endsWith(".jad")) {
            Toast.makeText(this, "Select the game's .jar file. JAD + paired-JAR import is not supported yet.", Toast.LENGTH_LONG).show();
            return;
        }
        prepareButtons[slot].setEnabled(false);
        convertButtons[slot].setEnabled(false);
        slotStatus[slot].setText("Importing and validating JAR…");
        importerExecutor.execute(() -> {
            try {
                MultiGamePackageStore.PreparedPackage result = MultiGamePackageStore.importJar(
                        getContentResolver(), source, getFilesDir(), slot);
                runOnUiThread(() -> {
                    prepareButtons[slot].setEnabled(true);
                    getSharedPreferences(PREFS, MODE_PRIVATE).edit()
                            .putString("prepared_slot" + slot, result.jarFile.getAbsolutePath())
                            .putString("prepared_vendor" + slot, result.vendor)
                            .putString("prepared_version" + slot, result.version)
                            .remove("prepared_midlet" + slot)
                            .remove("prepared_class" + slot)
                            .apply();
                    chooseMidletForSlot(slot, result);
                });
            } catch (IOException | RuntimeException e) {
                runOnUiThread(() -> {
                    prepareButtons[slot].setEnabled(true);
                    slotStatus[slot].setText("Prepare failed: " + e.getMessage());
                    Toast.makeText(this, "Cannot prepare JAR: " + e.getMessage(), Toast.LENGTH_LONG).show();
                });
            }
        });
    }



    private void chooseMidletForSlot(int slot, MultiGamePackageStore.PreparedPackage prepared) {
        if (prepared.midlets.size() <= 1) {
            saveMidletSelection(slot, prepared, 0);
            return;
        }
        String[] choices = new String[prepared.midlets.size()];
        for (int i = 0; i < choices.length; i++) choices[i] = prepared.midlets.get(i).toString();
        final int[] selected = {0};
        new AlertDialog.Builder(this)
                .setTitle("Choose MIDlet · Slot " + (slot + 1))
                .setSingleChoiceItems(choices, 0, (dialog, which) -> selected[0] = which)
                .setPositiveButton("Use MIDlet", (dialog, which) -> saveMidletSelection(slot, prepared, selected[0]))
                .setNegativeButton("Cancel", (dialog, which) -> {
                    convertButtons[slot].setEnabled(false);
                    slotStatus[slot].setText("JAR prepared; choose a MIDlet before conversion.");
                })
                .show();
    }

    private void saveMidletSelection(int slot, MultiGamePackageStore.PreparedPackage prepared, int index) {
        if (index < 0 || index >= prepared.midlets.size()) index = 0;
        MultiGamePackageStore.MidletEntry entry = prepared.midlets.get(index);
        getSharedPreferences(PREFS, MODE_PRIVATE).edit()
                .putString("prepared_midlet" + slot, entry.title)
                .putString("prepared_class" + slot, entry.className)
                .apply();
        convertButtons[slot].setEnabled(true);
        slotStatus[slot].setText("Prepared: " + entry.title + " · " + entry.className);
        Toast.makeText(this, "MIDlet selected. Convert it to runtime format next.", Toast.LENGTH_LONG).show();
    }

    private void convertSlot(int slot) {
        SharedPreferences prefs = getSharedPreferences(PREFS, MODE_PRIVATE);
        String preparedPath = prefs.getString("prepared_slot" + slot, null);
        String selectedClass = prefs.getString("prepared_class" + slot, null);
        if (preparedPath == null || !new File(preparedPath).isFile()
                || selectedClass == null || selectedClass.trim().isEmpty()) {
            Toast.makeText(this, "Prepare the JAR and choose a MIDlet for slot " + (slot + 1) + " first.", Toast.LENGTH_SHORT).show();
            return;
        }
        convertButtons[slot].setEnabled(false);
        prepareButtons[slot].setEnabled(false);
        slotStatus[slot].setText("Converting JAR to runtime dex/resource package…");
        importerExecutor.execute(() -> {
            try {
                MultiGameRuntimePreparer.RuntimePackage runtime =
                        MultiGameRuntimePreparer.prepare(new File(preparedPath), slot);
                prefs.edit().putString("runtime_slot" + slot, runtime.appDirectory.getAbsolutePath())
                        .putString("runtime_midlet" + slot, runtime.name)
                        .apply();
                runOnUiThread(() -> {
                    convertButtons[slot].setEnabled(true);
                    prepareButtons[slot].setEnabled(true);
                    slotStatus[slot].setText("Runtime ready: " + runtime.name + " · " + runtime.appDirectory.getName()
                            + ". Ready to launch in the grid.");
                    launchButtons[slot].setEnabled(true);
                    Toast.makeText(this, "Converted to J2ME-Loader runtime format for slot " + (slot + 1),
                            Toast.LENGTH_LONG).show();
                });
            } catch (IOException | ConverterException | RuntimeException e) {
                runOnUiThread(() -> {
                    convertButtons[slot].setEnabled(true);
                    prepareButtons[slot].setEnabled(true);
                    slotStatus[slot].setText("Runtime conversion failed: " + e.getMessage());
                    Toast.makeText(this, "Cannot convert JAR: " + e.getMessage(), Toast.LENGTH_LONG).show();
                });
            }
        });
    }

    /** Launch one converted MIDlet directly into its corresponding grid slot. */
    private void launchRuntimeInGrid(int slot) {
        SharedPreferences prefs = getSharedPreferences(PREFS, MODE_PRIVATE);
        String runtimePath = prefs.getString("runtime_slot" + slot, null);
        if (runtimePath == null) {
            Toast.makeText(this, "Convert this slot for runtime first.", Toast.LENGTH_SHORT).show();
            return;
        }
        File appDirectory = new File(runtimePath);
        if (!hasRuntimePackage(appDirectory)) {
            launchButtons[slot].setEnabled(false);
            slotStatus[slot].setText("Runtime package is incomplete; convert it again.");
            return;
        }
        try {
            File configDir = new File(Config.getConfigsDir(), appDirectory.getName());
            if (!configDir.isDirectory() && !configDir.mkdirs()) {
                throw new IOException("Cannot create game configuration directory");
            }
            File configFile = new File(configDir, Config.MIDLET_CONFIG_FILE);
            if (!configFile.isFile()) {
                ProfileModel defaults = new ProfileModel(configDir);
                if (!ProfilesManager.saveConfig(defaults)) {
                    throw new IOException("Cannot create default game configuration");
                }
            }
            launchButtons[slot].setEnabled(false);
            launchButtons[slot].setText("Starting · Slot " + (slot + 1));
            slotStatus[slot].setText("Starting MIDlet in grid…");
            String requestedMidletClass = prefs.getString("prepared_class" + slot, null);
            String midletName = MultiGameSessionLauncher.launch(this, appDirectory.getAbsolutePath(),
                    grid.getSlot(slot), grid.getSlot(slot).getOverlayView(), requestedMidletClass);
            launchButtons[slot].setText("Cancel start · Slot " + (slot + 1));
            launchButtons[slot].setEnabled(true);
            Toast.makeText(this, "Starting " + midletName + " in slot " + (slot + 1), Toast.LENGTH_SHORT).show();
        } catch (Exception e) {
            launchButtons[slot].setText("Launch in grid · Slot " + (slot + 1));
            slotStatus[slot].setText("Grid launch failed: " + e.getMessage());
            Toast.makeText(this, "Cannot launch slot " + (slot + 1) + ": " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    void onSlotRuntimeStarted(int slot, MIDlet midlet) {
        if (isFinishing() || isDestroyed()) return;
        slotStatus[slot].setText("Running in grid: " + midlet.getClass().getSimpleName());
        launchButtons[slot].setText("Stop game · Slot " + (slot + 1));
        launchButtons[slot].setEnabled(true);
    }

    void onSlotRuntimeStopped(int slot) {
        if (isFinishing() || isDestroyed()) return;
        slotStatus[slot].setText("Stopped");
        launchButtons[slot].setText("Launch in grid · Slot " + (slot + 1));
        launchButtons[slot].setEnabled(true);
    }

    void onSlotRuntimeFailed(int slot, Throwable error) {
        if (isFinishing() || isDestroyed()) return;
        String message = error == null || error.getMessage() == null
                ? "MIDlet initialization failed" : error.getMessage();
        slotStatus[slot].setText("Launch failed: " + message);
        launchButtons[slot].setText("Launch in grid · Slot " + (slot + 1));
        launchButtons[slot].setEnabled(true);
        Toast.makeText(this, "Slot " + (slot + 1) + " failed: " + message, Toast.LENGTH_LONG).show();
    }

    private static boolean hasRuntimePackage(File directory) {
        return directory != null && directory.isDirectory()
                && new File(directory, Config.MIDLET_DEX_FILE).isFile()
                && new File(directory, Config.MIDLET_RES_FILE).isFile()
                && new File(directory, Config.MIDLET_MANIFEST_FILE).isFile();
    }

    private String queryDisplayName(Uri uri) {
        try (Cursor cursor = getContentResolver().query(uri,
                new String[]{OpenableColumns.DISPLAY_NAME}, null, null, null)) {
            if (cursor != null && cursor.moveToFirst()) {
                int index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                if (index >= 0) {
                    String name = cursor.getString(index);
                    if (name != null && !name.isEmpty()) return name;
                }
            }
        } catch (Exception ignored) { }
        String last = uri.getLastPathSegment();
        return last == null ? "Selected game" : last;
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        if (grid != null && MultiGameInputRouter.dispatchToFocused(event)) return true;
        return super.dispatchKeyEvent(event);
    }

    @Override
    protected void onPause() {
        // Avoid leaving a game running invisibly when Android backgrounds this host.
        // Pause each MIDlet independently; never route through the legacy singleton.
        if (grid != null) {
            MultiGameInputRouter.releaseAllPressedKeys();
            for (int i = 0; i < MultiGameGridView.SLOT_COUNT; i++) {
                grid.getSlot(i).pauseSession();
            }
        }
        super.onPause();
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Each session checks its own registered Context before resuming.
        if (grid != null) {
            for (int i = 0; i < MultiGameGridView.SLOT_COUNT; i++) {
                grid.getSlot(i).resumeSession();
            }
        }
    }

    @Override
    protected void onDestroy() {
        if (grid != null) {
            for (int i = 0; i < MultiGameGridView.SLOT_COUNT; i++) {
                // Also cancel sessions still initializing, before a MIDlet owner is attached.
                grid.getSlot(i).stopSession();
            }
        }
        importerExecutor.shutdown();
        super.onDestroy();
    }

    @Override
    public void onBackPressed() {
        if (grid != null && grid.getMaximizedSlot() >= 0) {
            grid.restoreGrid();
            return;
        }
        super.onBackPressed();
    }
}
