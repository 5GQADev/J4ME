package javax.microedition.shell.multigame;

import android.content.Intent;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.provider.OpenableColumns;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
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
    private final Button[] configButtons = new Button[4];
    private final Button[] maximizeButtons = new Button[4];
    private LinearLayout rootLayout;
    private TextView toolbarTitle;
    private TextView hintView;
    private final TextView[] slotStatus = new TextView[4];
    private final ExecutorService importerExecutor = Executors.newFixedThreadPool(2);
    private ActivityResultLauncher<String[]> picker;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        picker = registerForActivityResult(new ActivityResultContracts.OpenDocument(), uri -> {
            if (uri == null || pendingSlot < 0) return;
            int slot = pendingSlot;
            if (isSlotRunning(slot)) {
                Toast.makeText(this, "Dừng game trước khi chọn JAR mới.", Toast.LENGTH_SHORT).show();
                return;
            }
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
            selectButtons[slot].setText("JAR");
            slotStatus[slot].setText("Đang chuẩn bị JAR…");
            setSlotBusy(slot, true);
            prepareSlot(slot);
        });

        final int muted = android.graphics.Color.rgb(71, 85, 105);

        LinearLayout root = new LinearLayout(this);
        rootLayout = root;
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(10), dp(6), dp(10), dp(8));

        LinearLayout toolbar = new LinearLayout(this);
        toolbar.setGravity(Gravity.CENTER_VERTICAL);
        TextView title = new TextView(this);
        toolbarTitle = title;
        title.setText("Multi-game");
        title.setTextSize(22);
        title.setTypeface(null, android.graphics.Typeface.BOLD);
        title.setPadding(dp(4), dp(8), dp(4), dp(8));
        toolbar.addView(title, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        Button more = new Button(this);
        more.setText("⋮");
        more.setTextSize(24);
        more.setMinWidth(dp(48));
        more.setAllCaps(false);
        more.setContentDescription("Tùy chọn");
        toolbar.addView(more, new LinearLayout.LayoutParams(dp(52), dp(48)));
        more.setOnClickListener(v -> showOptions(more));
        root.addView(toolbar, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        TextView hint = new TextView(this);
        hintView = hint;
        hint.setText("4 game · Dùng nút ⛶ trên mỗi ô để phóng to/thu nhỏ");
        hint.setTextSize(12);
        hint.setPadding(dp(4), 0, dp(4), dp(6));
        root.addView(hint, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        grid = new MultiGameGridView(this);
        root.addView(grid, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        for (int i = 0; i < 4; i++) {
            final int slot = i;
            selectButtons[i] = new Button(this);
            selectButtons[i].setAllCaps(false);
            selectButtons[i].setText("JAR");
            selectButtons[i].setTextSize(10);
            selectButtons[i].setMinHeight(dp(38));
            selectButtons[i].setOnClickListener(v -> {
                if (isSlotRunning(slot)) {
                    Toast.makeText(this, "Dừng game trước khi chọn JAR mới.", Toast.LENGTH_SHORT).show();
                    return;
                }
                pendingSlot = slot;
                picker.launch(new String[]{"application/java-archive", "application/octet-stream", "*/*"});
            });
            prepareButtons[i] = new Button(this); // internal workflow state; not displayed separately
            convertButtons[i] = new Button(this); // import now prepares and converts automatically
            launchButtons[i] = new Button(this);
            launchButtons[i].setAllCaps(false);
            launchButtons[i].setText("▶");
            launchButtons[i].setTextSize(10);
            launchButtons[i].setMinHeight(dp(38));
            launchButtons[i].setOnClickListener(v -> {
                MultiGameGridView.SlotHost host = grid.getSlot(slot);
                if (host.hasOwner() || MultiGameSessionLauncher.hasSessionOrLaunching(host)) {
                    host.stopSession();
                    launchButtons[slot].setText("▶");
                    slotStatus[slot].setText("Stop requested");
                } else launchRuntimeInGrid(slot);
            });
            Button config = new Button(this);
            configButtons[i] = config;
            config.setAllCaps(false);
            config.setText("SET");
            config.setTextSize(10);
            config.setMinHeight(dp(38));
            config.setEnabled(false);
            config.setOnClickListener(v -> showSlotConfig(slot));
            maximizeButtons[i] = new Button(this);
            maximizeButtons[i].setAllCaps(false);
            maximizeButtons[i].setText("⛶");
            maximizeButtons[i].setTextSize(10);
            maximizeButtons[i].setMinHeight(dp(38));
            maximizeButtons[i].setContentDescription("Phóng to/thu nhỏ ô " + (slot + 1));
            maximizeButtons[i].setOnClickListener(v -> {
                if (grid.getMaximizedSlot() == slot) grid.restoreGrid(); else grid.maximize(slot);
            });
            slotStatus[i] = new TextView(this);
            slotStatus[i].setText("Chưa có game");
            slotStatus[i].setTextSize(10);
            slotStatus[i].setTextColor(muted);
            grid.getSlot(i).addControlBar(selectButtons[i], config, launchButtons[i], maximizeButtons[i], slotStatus[i]);
        }

        setContentView(root);
        applyThemeColors();
        restoreSelections();
    }

    private void showOptions(View anchor) {
        androidx.appcompat.widget.PopupMenu menu = new androidx.appcompat.widget.PopupMenu(this, anchor);
        menu.getMenu().add(0, 1, 0, "Không tắt màn hình").setCheckable(true)
                .setChecked(androidx.preference.PreferenceManager.getDefaultSharedPreferences(this)
                        .getBoolean(ru.playsoftware.j2meloader.util.Constants.PREF_KEEP_SCREEN, false));
        menu.getMenu().add(0, 2, 1, "Cài đặt ứng dụng");
        menu.getMenu().add(0, 4, 2, "Hồ sơ cấu hình");
        menu.setOnMenuItemClickListener(item -> {
            if (item.getItemId() == 1) {
                boolean enabled = !androidx.preference.PreferenceManager.getDefaultSharedPreferences(this)
                        .getBoolean(ru.playsoftware.j2meloader.util.Constants.PREF_KEEP_SCREEN, false);
                androidx.preference.PreferenceManager.getDefaultSharedPreferences(this).edit()
                        .putBoolean(ru.playsoftware.j2meloader.util.Constants.PREF_KEEP_SCREEN, enabled).apply();
                applyKeepScreenOn();
                return true;
            } else if (item.getItemId() == 2) {
                startActivity(new Intent(this, ru.playsoftware.j2meloader.settings.SettingsActivity.class));
                return true;
            } else if (item.getItemId() == 4) {
                startActivity(new Intent(this, ru.playsoftware.j2meloader.config.ProfilesActivity.class));
                return true;
            }
            return false;
        });
        menu.show();
    }

    private void applyKeepScreenOn() {
        boolean keep = androidx.preference.PreferenceManager.getDefaultSharedPreferences(this)
                .getBoolean(ru.playsoftware.j2meloader.util.Constants.PREF_KEEP_SCREEN, false);
        if (keep) getWindow().addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        else getWindow().clearFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
    }

    private void showSlotConfig(int slot) {
        if (!isSlotConverted(slot) || grid.getSlot(slot).hasOwner()
                || MultiGameSessionLauncher.hasSessionOrLaunching(grid.getSlot(slot))) {
            Toast.makeText(this, "Cấu hình chỉ dùng được khi game đã convert và chưa chạy.", Toast.LENGTH_SHORT).show();
            return;
        }
        SharedPreferences prefs = getSharedPreferences(PREFS, MODE_PRIVATE);
        String runtimePath = prefs.getString("runtime_slot" + slot, null);
        if (runtimePath == null) return;
        File appDirectory = new File(runtimePath);
        Intent intent = new Intent(ru.playsoftware.j2meloader.util.Constants.ACTION_EDIT,
                Uri.parse(appDirectory.getAbsolutePath()), this, ru.playsoftware.j2meloader.config.ConfigActivity.class);
        intent.putExtra(ru.playsoftware.j2meloader.util.Constants.KEY_MIDLET_NAME,
                prefs.getString("runtime_midlet" + slot, selectedNames[slot]));
        startActivity(intent);
    }

    private boolean isSlotRunning(int slot) {
        return grid != null && (grid.getSlot(slot).hasOwner()
                || MultiGameSessionLauncher.hasSessionOrLaunching(grid.getSlot(slot)));
    }

    private boolean isSlotConverted(int slot) {
        String path = getSharedPreferences(PREFS, MODE_PRIVATE).getString("runtime_slot" + slot, null);
        return path != null && hasRuntimePackage(new File(path));
    }

    private void applyThemeColors() {
        boolean dark = (getResources().getConfiguration().uiMode &
                android.content.res.Configuration.UI_MODE_NIGHT_MASK) ==
                android.content.res.Configuration.UI_MODE_NIGHT_YES;
        int surface = dark ? android.graphics.Color.rgb(24, 26, 31) : android.graphics.Color.rgb(245, 247, 250);
        int card = dark ? android.graphics.Color.rgb(42, 45, 52) : android.graphics.Color.rgb(226, 232, 240);
        int foreground = dark ? android.graphics.Color.rgb(238, 240, 244) : android.graphics.Color.rgb(15, 23, 42);
        int muted = dark ? android.graphics.Color.rgb(180, 187, 199) : android.graphics.Color.rgb(71, 85, 105);
        rootLayout.setBackgroundColor(surface);
        toolbarTitle.setTextColor(foreground);
        hintView.setTextColor(muted);
        grid.setBackgroundColor(dark ? android.graphics.Color.rgb(18, 20, 24) : android.graphics.Color.rgb(203, 213, 225));
        grid.setSlotColors(card, foreground, dark);
        for (int i = 0; i < 4; i++) {
            slotStatus[i].setTextColor(dark ? android.graphics.Color.rgb(190, 198, 210) : android.graphics.Color.rgb(71, 85, 105));
            selectButtons[i].setTextColor(foreground);
            configButtons[i].setTextColor(foreground);
            launchButtons[i].setTextColor(foreground);
            maximizeButtons[i].setTextColor(foreground);
        }
    }

    private LinearLayout.LayoutParams controlButtonParams() {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        params.setMargins(dp(2), dp(3), dp(2), dp(3));
        return params;
    }

    private void setSlotBusy(int slot, boolean busy) {
        selectButtons[slot].setEnabled(!busy && !isSlotRunning(slot));
        launchButtons[slot].setEnabled(!busy);
        // Configuration button is the second child in the slot control strip.
        grid.getSlot(slot).setControlsEnabled(!busy);
        configButtons[slot].setEnabled(!busy && isSlotConverted(slot)
                && !grid.getSlot(slot).hasOwner()
                && !MultiGameSessionLauncher.hasSessionOrLaunching(grid.getSlot(slot)));
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
            selectButtons[i].setText("JAR");
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
                configButtons[i].setEnabled(!grid.getSlot(i).hasOwner());
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
            setSlotBusy(slot, false);
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
                    setSlotBusy(slot, false);
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
                    setSlotBusy(slot, false);
                    slotStatus[slot].setText("Đã chọn JAR; chưa convert");
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
        slotStatus[slot].setText("Đã chọn MIDlet · đang convert…");
        convertSlot(slot);
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
                    setSlotBusy(slot, false);
                    slotStatus[slot].setText("Sẵn sàng: " + runtime.name);
                    launchButtons[slot].setEnabled(true);
                    configButtons[slot].setEnabled(true);
                    Toast.makeText(this, "Converted to J2ME-Loader runtime format for slot " + (slot + 1),
                            Toast.LENGTH_LONG).show();
                });
            } catch (IOException | ConverterException | RuntimeException e) {
                runOnUiThread(() -> {
                    setSlotBusy(slot, false);
                    slotStatus[slot].setText("Convert lỗi: " + e.getMessage());
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
            selectButtons[slot].setEnabled(false);
            File configDir = new File(Config.getConfigsDir(), appDirectory.getName());
            if (!configDir.isDirectory() && !configDir.mkdirs()) {
                throw new IOException("Cannot create game configuration directory");
            }
            File configFile = new File(configDir, Config.MIDLET_CONFIG_FILE);
            ProfileModel profile = configFile.isFile() ? ProfilesManager.loadConfig(configDir) : null;
            if (profile == null) profile = new ProfileModel(configDir);
            int sizePreset = getSharedPreferences(PREFS, MODE_PRIVATE).getInt("slot_size" + slot, 4);
            switch (sizePreset) {
                case 0: profile.screenWidth = 320; profile.screenHeight = 240; break;
                case 1: profile.screenWidth = 240; profile.screenHeight = 320; break;
                case 2: profile.screenWidth = 640; profile.screenHeight = 480; break;
                case 3: profile.screenWidth = 480; profile.screenHeight = 640; break;
                default: break; // Auto keeps the profile's existing dimensions.
            }
            if (!ProfilesManager.saveConfig(profile)) {
                throw new IOException("Cannot save game configuration");
            }
            launchButtons[slot].setEnabled(false);
            launchButtons[slot].setText("…");
            slotStatus[slot].setText("Starting MIDlet in grid…");
            String requestedMidletClass = prefs.getString("prepared_class" + slot, null);
            String midletName = MultiGameSessionLauncher.launch(this, appDirectory.getAbsolutePath(),
                    grid.getSlot(slot), grid.getSlot(slot).getOverlayView(), requestedMidletClass);
            launchButtons[slot].setText("■");
            launchButtons[slot].setEnabled(true);
            Toast.makeText(this, "Starting " + midletName + " in slot " + (slot + 1), Toast.LENGTH_SHORT).show();
        } catch (Exception e) {
            selectButtons[slot].setEnabled(true);
            launchButtons[slot].setText("▶");
            slotStatus[slot].setText("Grid launch failed: " + e.getMessage());
            Toast.makeText(this, "Cannot launch slot " + (slot + 1) + ": " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    void onSlotRuntimeStarted(int slot, MIDlet midlet) {
        if (isFinishing() || isDestroyed()) return;
        slotStatus[slot].setText("Running in grid: " + midlet.getClass().getSimpleName());
        selectButtons[slot].setEnabled(false);
        launchButtons[slot].setText("■");
        launchButtons[slot].setEnabled(true);
        configButtons[slot].setEnabled(false);
    }

    void onSlotRuntimeStopped(int slot) {
        if (isFinishing() || isDestroyed()) return;
        slotStatus[slot].setText(selectedNames[slot] == null ? "Đã dừng" : selectedNames[slot]);
        selectButtons[slot].setEnabled(true);
        launchButtons[slot].setText("▶");
        launchButtons[slot].setEnabled(true);
        configButtons[slot].setEnabled(isSlotConverted(slot));
    }

    void onSlotRuntimeFailed(int slot, Throwable error) {
        if (isFinishing() || isDestroyed()) return;
        String message = error == null || error.getMessage() == null
                ? "MIDlet initialization failed" : error.getMessage();
        slotStatus[slot].setText("Launch failed: " + message);
        selectButtons[slot].setEnabled(true);
        launchButtons[slot].setText("▶");
        launchButtons[slot].setEnabled(true);
        configButtons[slot].setEnabled(isSlotConverted(slot));
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
        applyKeepScreenOn();
        applyThemeColors();
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
