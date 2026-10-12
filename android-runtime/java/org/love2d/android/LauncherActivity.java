package org.love2d.android;

import android.app.*;
import android.content.*;
import android.os.Bundle;
import android.view.View;
import android.widget.*;
import java.util.*;
import java.util.concurrent.*;
import org.json.*;

/** Phone-side mod manager. Does not load SDL, LuaJIT, or Lovely. */
public class LauncherActivity extends Activity {
    private RuntimeStore store;
    private LinearLayout layout;
    private TextView status, installed;
    private Spinner versions;
    private EditText catalog;
    private final List<View> controls = new ArrayList<>();
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private boolean busy;
    private String zipName;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        if (state != null) zipName = state.getString("zipName");
        ScrollView scroll = new ScrollView(this);
        layout = new LinearLayout(this); layout.setOrientation(LinearLayout.VERTICAL);
        int pad = (int) (20 * getResources().getDisplayMetrics().density);
        layout.setPadding(pad, pad, pad, pad); scroll.addView(layout); setContentView(scroll);
        text("Lovely Mobile Maker", 26);
        text("Choose Lovely and update mods here, then launch Balatro. Updates require an internet connection.", 16);
        installed = text("Preparing…", 16);
        versions = new Spinner(this); layout.addView(versions); controls.add(versions);
        button("Use selected Lovely version", () -> job(() -> {
            store.select((String) versions.getSelectedItem());
            return "Lovely " + store.selected() + " selected for your next game.";
        }));
        text("Mod versions", 20);
        text("Choose the version you want. Older releases and prereleases are available; installing a mod will not update Steamodded automatically.", 16);
        for (String name : new String[]{"Steamodded", "Multiplayer", "JokerDisplay", "Blueprint"})
            button("Choose / install " + name + " version", () -> {
                try { browseReleases(name, RuntimeStore.repositoryFor(name), 1); }
                catch (Exception e) { status.setText(e.getMessage()); }
            });
        button("Install another mod from GitHub", this::githubMod);
        button("Import a mod ZIP from this phone", this::chooseZipName);
        button("Manage installed mods / restore previous versions", this::manageMods);
        button("Launch game", () -> job(() -> {
            store.select(store.selected()); store.selectedLibrary();
            runOnUiThread(() -> startActivity(new Intent(this, ManagedGameActivity.class)));
            return "Game opened. Exit the game to change versions or update mods.";
        }));
        text("Multiplayer needs Lovely 0.9+, Steamodded, and compatible game files. Keep the same Multiplayer version as your friends.", 16);
        text("Additional Lovely versions", 20);
        text("The APK includes 0.9.0 and 0.10.0. For future Android builds, enter an HTTPS catalog URL from a maintainer you trust.", 16);
        catalog = new EditText(this); catalog.setSingleLine(true); catalog.setHint("https://…/lovely-catalog.json");
        catalog.setInputType(android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_VARIATION_URI);
        catalog.setText(getSharedPreferences("lmm", 0).getString("catalog", ""));
        layout.addView(catalog); controls.add(catalog);
        button("Get Lovely versions from catalog", this::checkCatalog);
        status = text("", 16);
        try {
            store = new RuntimeStore(this);
            job(() -> { store.recoverMods(); store.select(store.selected()); return "Ready. Lovely " + store.selected() + " is selected."; });
        } catch (Exception e) { status.setText(e.getMessage()); enable(false); }
    }

    private TextView text(String value, int size) {
        TextView view = new TextView(this); view.setText(value); view.setTextSize(size);
        view.setPadding(0, 12, 0, 12); layout.addView(view); return view;
    }

    private void button(String title, Runnable action) {
        Button button = new Button(this); button.setText(title);
        button.setOnClickListener(v -> action.run()); layout.addView(button); controls.add(button);
    }

    private boolean gameRunning() {
        ActivityManager manager = (ActivityManager) getSystemService(ACTIVITY_SERVICE);
        List<ActivityManager.RunningAppProcessInfo> processes = manager.getRunningAppProcesses();
        if (processes != null) for (ActivityManager.RunningAppProcessInfo process : processes)
            if (process.processName.equals(getPackageName() + ":game")) return true;
        return false;
    }

    private void enable(boolean enabled) { for (View view : controls) view.setEnabled(enabled); }

    private void refresh() {
        try {
            List<String> names = store.versions();
            versions.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, names));
            versions.setSelection(Math.max(0, names.indexOf(store.selected())));
            StringBuilder summary = new StringBuilder("Lovely: " + store.selected() + " (" + store.abi + ")");
            List<String> mods = store.managedMods();
            if (mods.isEmpty()) summary.append("\nNo mods installed by this manager yet.");
            for (String name : mods) summary.append("\n").append(name).append(": ").append(store.modVersion(name))
                .append(store.isEnabled(name) ? "" : " [disabled]");
            installed.setText(summary.toString());
        } catch (Exception e) { status.setText(e.getMessage()); }
    }

    private void job(Callable<String> task) {
        if (busy) return;
        if (gameRunning()) { status.setText("Exit the running game before updating or starting another game."); return; }
        busy = true; enable(false); status.setText("Working… Please keep this screen open.");
        worker.submit(() -> {
            String result;
            try { result = task.call(); } catch (Exception e) { result = "Could not finish: " + e.getMessage(); }
            final String message = result;
            runOnUiThread(() -> {
                if (isFinishing() || isDestroyed()) return;
                busy = false; enable(true); refresh(); status.setText(message);
            });
        });
    }

    private void checkCatalog() {
        final String url = catalog.getText().toString().trim();
        job(() -> {
            List<String> names = store.onlineVersions(url);
            if (names.isEmpty()) return "No builds are available for " + store.abi + ".";
            getSharedPreferences("lmm", 0).edit().putString("catalog", url).apply();
            runOnUiThread(() -> new AlertDialog.Builder(this)
                .setTitle("Download Android Lovely")
                .setItems(names.toArray(new String[0]), (dialog, which) -> {
                    final String version = names.get(which);
                    new AlertDialog.Builder(this).setTitle("Install Lovely " + version + "?")
                        .setMessage("This downloads executable game code from the catalog you entered. Only install builds from a maintainer you trust.")
                        .setNegativeButton("Cancel", null)
                        .setPositiveButton("Install", (d, w) -> job(() -> {
                            store.downloadLovely(url, version); return "Lovely " + version + " downloaded and selected.";
                        })).show();
                }).setNegativeButton("Cancel", null).show());
            return "Choose a version from the catalog.";
        });
    }

    private EditText field(LinearLayout box, String hint, String initial) {
        EditText input = new EditText(this); input.setSingleLine(true); input.setHint(hint); input.setText(initial);
        input.setInputType(android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        box.addView(input); return input;
    }

    private LinearLayout form() {
        LinearLayout box = new LinearLayout(this); box.setOrientation(LinearLayout.VERTICAL);
        int pad = (int) (20 * getResources().getDisplayMetrics().density); box.setPadding(pad, 0, pad, 0);
        return box;
    }

    private void githubMod() {
        LinearLayout box = form();
        EditText repository = field(box, "GitHub URL or owner/repo", "");
        EditText folder = field(box, "Mod folder name (optional)", "");
        new AlertDialog.Builder(this).setTitle("Install a GitHub mod").setView(box)
            .setMessage("Choose a repository containing one Lua/Lovely mod. Install its dependencies separately. Existing mod folders are replaced only when you confirm an install.")
            .setNegativeButton("Cancel", null).setPositiveButton("Choose version", (dialog, which) -> {
                try {
                    String repo = ModFiles.repository(repository.getText().toString());
                    String name = folder.getText().toString().trim();
                    if (name.isEmpty()) {
                        name = repo.substring(repo.indexOf('/') + 1);
                        for (String known : new String[]{"Steamodded", "Multiplayer", "JokerDisplay", "Blueprint"})
                            if (repo.equalsIgnoreCase(RuntimeStore.repositoryFor(known))) name = known;
                    }
                    browseReleases(ModFiles.name(name), repo, 1);
                } catch (Exception e) { status.setText(e.getMessage()); }
            }).show();
    }

    private void browseReleases(String name, String repo, int page) {
        job(() -> {
            List<JSONObject> releases = store.releases(repo, page);
            String[] labels = new String[releases.size()];
            for (int i = 0; i < labels.length; i++) {
                JSONObject release = releases.get(i);
                labels[i] = release.getString("tag_name") + (release.optBoolean("prerelease") ? " [prerelease]" : "")
                    + "\n" + release.optString("published_at", "").replace('T', ' ').replace("Z", "");
            }
            runOnUiThread(() -> {
                if (isFinishing() || isDestroyed()) return;
                AlertDialog.Builder dialog = new AlertDialog.Builder(this).setTitle(name + " — releases, page " + page);
                if (labels.length > 0) dialog.setItems(labels, (d, which) -> chooseAsset(name, repo, releases.get(which)));
                else dialog.setMessage("No releases on this page. Enter a tag, branch, or commit to install from source.");
                if (labels.length == 30) dialog.setPositiveButton("Older releases", (d, w) -> browseReleases(name, repo, page + 1));
                dialog.setNeutralButton("Tag / branch / SHA", (d, w) -> customRef(name, repo));
                dialog.setNegativeButton(page > 1 ? "Newer releases" : "Cancel", (d, w) -> {
                    if (page > 1) browseReleases(name, repo, page - 1);
                }).show();
            });
            return "Select a " + name + " release, or enter an exact tag, branch, or commit. Current files are unchanged.";
        });
    }

    private void chooseAsset(String name, String repo, JSONObject release) {
        try {
            List<JSONObject> zips = new ArrayList<>();
            JSONArray assets = release.optJSONArray("assets");
            if (assets != null) for (int i = 0; i < assets.length(); i++) {
                JSONObject asset = assets.getJSONObject(i);
                if (asset.getString("name").toLowerCase(Locale.ROOT).endsWith(".zip")) zips.add(asset);
            }
            if (zips.isEmpty() || repo.equalsIgnoreCase(RuntimeStore.repositoryFor("Multiplayer"))) {
                confirmInstall(name, repo, release.getString("tag_name"), () -> store.installRelease(name, repo, release)); return;
            }
            String[] labels = new String[zips.size() + 1]; labels[0] = "Source code ZIP at this tag";
            for (int i = 0; i < zips.size(); i++) labels[i + 1] = zips.get(i).getString("name");
            new AlertDialog.Builder(this).setTitle("Choose " + name + " archive").setItems(labels, (d, which) -> {
                try {
                    confirmInstall(name, repo, release.getString("tag_name"), () -> which == 0
                        ? store.installRelease(name, repo, release) : store.installAsset(name, repo, release, zips.get(which - 1)));
                } catch (Exception e) { status.setText(e.getMessage()); }
            }).setNegativeButton("Cancel", null).show();
        } catch (Exception e) { status.setText(e.getMessage()); }
    }

    private void confirmInstall(String name, String repo, String version, Callable<String> action) {
        new AlertDialog.Builder(this).setTitle("Install " + name + " " + version + "?")
            .setMessage("Source: github.com/" + repo + "\nThis replaces this mod's files and keeps one previous version for restore. Install only mods you trust. Check the mod's dependencies and compatibility with your selected Steamodded, Lovely, and Multiplayer versions.")
            .setNegativeButton("Cancel", null).setPositiveButton("Install", (d, w) -> job(action)).show();
    }

    private void customRef(String name, String repo) {
        LinearLayout box = form(); EditText ref = field(box, "Exact tag, branch, or commit SHA", "");
        new AlertDialog.Builder(this).setTitle(name + " source version").setView(box)
            .setMessage("Use the exact GitHub tag, for example 1.0.0-beta-1814a. Steamodded's in-game version text can differ from its GitHub tag. Branches are resolved to a commit; a new install is needed to fetch later changes.")
            .setNegativeButton("Cancel", null).setPositiveButton("Continue", (d, w) -> {
                try {
                    String value = ModFiles.ref(ref.getText().toString());
                    confirmInstall(name, repo, value, () -> store.installRef(name, repo, value));
                } catch (Exception e) { status.setText(e.getMessage()); }
            }).show();
    }

    private void chooseZipName() {
        LinearLayout box = form(); EditText name = field(box, "Mod folder name", "");
        new AlertDialog.Builder(this).setTitle("Import one mod ZIP").setView(box)
            .setMessage("Pick a downloaded ZIP using Android's file picker. Use the existing folder name to replace that mod. Install its dependencies separately.")
            .setNegativeButton("Cancel", null).setPositiveButton("Choose ZIP", (d, w) -> {
                try {
                    zipName = ModFiles.name(name.getText().toString());
                    Intent picker = new Intent(Intent.ACTION_OPEN_DOCUMENT).setType("*/*").addCategory(Intent.CATEGORY_OPENABLE);
                    startActivityForResult(picker, 100);
                } catch (Exception e) { status.setText(e.getMessage()); }
            }).show();
    }

    @Override protected void onActivityResult(int request, int result, Intent data) {
        super.onActivityResult(request, result, data);
        if (request != 100 || result != RESULT_OK || data == null || data.getData() == null || zipName == null) return;
        final String name = zipName; final android.net.Uri uri = data.getData(); zipName = null;
        new AlertDialog.Builder(this).setTitle("Import " + name + "?")
            .setMessage("This replaces this mod's folder with files from the selected ZIP. Keep one previous version for restore. Only import mods you trust.")
            .setNegativeButton("Cancel", null).setPositiveButton("Import", (d, w) -> job(() -> {
                java.io.InputStream in = getContentResolver().openInputStream(uri);
                if (in == null) throw new java.io.IOException("Cannot open selected ZIP");
                return store.importZip(in, name);
            })).show();
    }

    private void manageMods() {
        try {
            List<String> names = store.managedMods();
            if (names.isEmpty()) { status.setText("No managed mods yet. Choose a release or import a mod ZIP first."); return; }
            String[] labels = new String[names.size()];
            for (int i = 0; i < labels.length; i++) labels[i] = names.get(i) + " — " + store.modVersion(names.get(i))
                + (store.isEnabled(names.get(i)) ? "" : " [disabled]");
            new AlertDialog.Builder(this).setTitle("Installed mods").setItems(labels, (d, which) -> modActions(names.get(which)))
                .setNegativeButton("Cancel", null).show();
        } catch (Exception e) { status.setText(e.getMessage()); }
    }

    private void modActions(String name) {
        try {
            String repo = store.source(name).optString("repo", "");
            boolean enabled = store.isEnabled(name);
            String[] actions = {"Choose a different version", "Restore previous version", enabled ? "Disable mod" : "Enable mod"};
            new AlertDialog.Builder(this).setTitle(name).setItems(actions, (d, which) -> {
                if (which == 0) {
                    if (repo.isEmpty()) status.setText("Imported ZIP: use Import a mod ZIP again with folder name " + name + ".");
                    else browseReleases(name, repo, 1);
                } else if (which == 1) job(() -> { store.rollback(name); return "Previous " + name + " restored."; });
                else job(() -> { store.toggleMod(name); return name + (store.isEnabled(name) ? " enabled." : " disabled."); });
            }).setNegativeButton("Cancel", null).show();
        } catch (Exception e) { status.setText(e.getMessage()); }
    }

    @Override protected void onSaveInstanceState(Bundle state) {
        super.onSaveInstanceState(state); state.putString("zipName", zipName);
    }

    @Override public void onBackPressed() { if (!busy) super.onBackPressed(); }
    @Override protected void onDestroy() { worker.shutdown(); super.onDestroy(); }
}
