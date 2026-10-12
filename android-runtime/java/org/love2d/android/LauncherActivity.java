package org.love2d.android;

import android.app.*;
import android.content.*;
import android.os.Bundle;
import android.webkit.*;
import android.net.Uri;
import java.io.*;
import android.widget.*;
import java.util.*;
import java.util.concurrent.*;
import org.json.*;

/** Phone-side mod manager. Does not load SDL, LuaJIT, or Lovely. */
public class LauncherActivity extends Activity {
    private RuntimeStore store;
    private WebView web;
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private boolean busy, pageReady;
    private String zipName, lastMessage = "Preparing your game…";
    private static final String LOCAL = "https://appassets.androidplatform.net/assets/lmm/ui/";

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        if (state != null) zipName = state.getString("zipName");
        getWindow().setStatusBarColor(0xffa53535);
        getWindow().setNavigationBarColor(0xff802e31);
        web = new WebView(this);
        web.setBackgroundColor(0xffa53535);
        WebSettings settings = web.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setAllowFileAccess(false);
        settings.setAllowContentAccess(false);
        settings.setAllowFileAccessFromFileURLs(false);
        settings.setAllowUniversalAccessFromFileURLs(false);
        settings.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
        settings.setJavaScriptCanOpenWindowsAutomatically(false);
        web.addJavascriptInterface(new LocalBridge(), "LmmNative");
        web.setWebViewClient(new WebViewClient() {
            @Override public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) { return true; }
            @Override public boolean shouldOverrideUrlLoading(WebView view, String url) { return true; }
            @Override public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest request) {
                // This origin never goes over the network. Only four bundled assets are served.
                Uri uri = request.getUrl();
                String name = uri.getLastPathSegment();
                boolean allowed = "GET".equals(request.getMethod()) && "https".equals(uri.getScheme())
                    && "appassets.androidplatform.net".equals(uri.getHost()) && uri.getPort() == -1
                    && uri.getQuery() == null && uri.getFragment() == null
                    && ("/assets/lmm/ui/" + name).equals(uri.getPath())
                    && Arrays.asList("index.html", "launcher.css", "launcher.js", "PixelifySans.ttf").contains(name);
                if (allowed) try {
                    String mime = name.endsWith("html") ? "text/html" : name.endsWith("css") ? "text/css"
                        : name.endsWith("js") ? "application/javascript" : "font/ttf";
                    return new WebResourceResponse(mime, name.endsWith("ttf") ? null : "UTF-8",
                        getAssets().open("lmm/ui/" + name));
                } catch (IOException ignored) { }
                return new WebResourceResponse("text/plain", "UTF-8", 403, "Blocked",
                    Collections.emptyMap(), new ByteArrayInputStream(new byte[0]));
            }
        });
        setContentView(web);
        try { store = new RuntimeStore(this); }
        catch (Exception e) { lastMessage = "Cannot prepare game: " + e.getMessage(); }
        web.loadUrl(LOCAL + "index.html");
    }

    /** Only trusted packaged UI is loaded into this WebView. All downloads use Downloads. */
    private final class LocalBridge {
        @JavascriptInterface public void command(String action, String value) {
            runOnUiThread(() -> {
                if (isFinishing() || isDestroyed()) return;
                if (action.equals("init")) {
                    pageReady = true;
                    if (store == null) { status(lastMessage); return; }
                    if (gameRunning()) { refresh(); status("Exit the game before changing mods or Lovely."); return; }
                    job(() -> { store.recoverMods(); store.select(store.selected()); return ""; });
                    return;
                }
                if (busy || store == null) return;
                try {
                    switch (action) {
                        case "launch": job(() -> {
                            store.select(store.selected()); store.selectedLibrary();
                            runOnUiThread(() -> startActivity(new Intent(LauncherActivity.this, ManagedGameActivity.class)));
                            return "Game opened. Exit the game to change mods or Lovely.";
                        }); break;
                        case "select": job(() -> { store.select(value); return "Lovely " + store.selected() + " selected for your next game."; }); break;
                        case "browse": {
                            String name = ModFiles.name(value);
                            String repo = store.source(name).optString("repo", "");
                            if (repo.isEmpty()) try { repo = RuntimeStore.repositoryFor(name); } catch (IOException ignored) { }
                            name = store.installedName(name, repo, "");
                            if (repo.isEmpty()) { status("Imported ZIP: import another ZIP with the same folder name, " + name + "."); break; }
                            browseReleases(name, repo, 1); break;
                        }
                        case "toggle": job(() -> { store.toggleMod(value); return value + (store.isEnabled(value) ? " enabled." : " disabled."); }); break;
                        case "restore": job(() -> { store.rollback(value); return "Previous " + value + " restored."; }); break;
                        case "githubNative": githubMod(); break;
                        case "zipNative": chooseZipName(); break;
                        case "catalog": catalogDialog(); break;
                        default: status("Unknown launcher action.");
                    }
                } catch (Exception e) { status(e.getMessage()); }
            });
        }
    }

    private void script(String call) {
        if (pageReady && web != null && !isFinishing() && !isDestroyed())
            web.evaluateJavascript("window.LmmApp && " + call, null);
    }

    private void status(String value) {
        lastMessage = value == null ? "" : value;
        script("window.LmmApp.status(" + JSONObject.quote(lastMessage) + "," + busy + ")");
    }

    private void catalogDialog() {
        LinearLayout box = form();
        EditText input = field(box, "https://…/lovely-catalog.json", getSharedPreferences("lmm", 0).getString("catalog", ""));
        new AlertDialog.Builder(this).setTitle("Additional Android Lovely builds").setView(box)
            .setMessage("Optional: enter a maintainer's HTTPS catalog. Included Lovely versions already work offline.")
            .setNegativeButton("Cancel", null).setPositiveButton("Check versions", (d, w) -> checkCatalog(input.getText().toString().trim())).show();
    }

    private boolean gameRunning() {
        ActivityManager manager = (ActivityManager) getSystemService(ACTIVITY_SERVICE);
        List<ActivityManager.RunningAppProcessInfo> processes = manager.getRunningAppProcesses();
        if (processes != null) for (ActivityManager.RunningAppProcessInfo process : processes)
            if (process.processName.equals(getPackageName() + ":game")) return true;
        return false;
    }

    private void refresh() {
        if (store == null) return;
        try {
            JSONObject snapshot = new JSONObject().put("selected", store.selected()).put("ready", true);
            snapshot.put("versions", new JSONArray(store.versions()));
            JSONArray mods = new JSONArray();
            for (String name : store.managedMods()) mods.put(store.modInfo(name));
            snapshot.put("mods", mods);
            script("window.LmmApp.update(" + snapshot.toString() + ")");
        } catch (Exception e) { status(e.getMessage()); }
    }

    private void job(Callable<String> task) {
        if (busy) return;
        if (gameRunning()) { status("Exit the running game before updating or starting another game."); return; }
        busy = true; status("Working… Please keep this screen open.");
        worker.submit(() -> {
            String result;
            try { result = task.call(); } catch (Exception e) { result = "Could not finish: " + e.getMessage(); }
            final String message = result;
            runOnUiThread(() -> {
                if (isFinishing() || isDestroyed()) return;
                busy = false; refresh(); status(message);
            });
        });
    }

    private void checkCatalog(final String url) {
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
                        for (String known : new String[]{"Steamodded", "Multiplayer", "JokerDisplay", "Blueprint", "WhatsInMyFool"})
                            if (repo.equalsIgnoreCase(RuntimeStore.repositoryFor(known))) name = known;
                    }
                    browseReleases(ModFiles.name(name), repo, 1);
                } catch (Exception e) { status(e.getMessage()); }
            }).show();
    }

    private void browseReleases(String name, String repo, int page) {
        job(() -> {
            List<JSONObject> releases = store.releases(repo, page);
            final boolean fool = repo.equalsIgnoreCase(RuntimeStore.repositoryFor("WhatsInMyFool"));
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
                else dialog.setMessage(fool
                    ? "No published releases. Install current source to download main at its exact commit. Requires Steamodded."
                    : "No releases on this page. Enter a tag, branch, or commit to install from source.");
                if (fool && labels.length == 0 && page == 1)
                    dialog.setPositiveButton("Current source", (d, w) -> confirmInstall(name, repo, "main", () -> store.installRef(name, repo, "main")));
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
                } catch (Exception e) { status(e.getMessage()); }
            }).setNegativeButton("Cancel", null).show();
        } catch (Exception e) { status(e.getMessage()); }
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
                } catch (Exception e) { status(e.getMessage()); }
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
                } catch (Exception e) { status(e.getMessage()); }
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
            if (names.isEmpty()) { status("No managed mods yet. Choose a release or import a mod ZIP first."); return; }
            String[] labels = new String[names.size()];
            for (int i = 0; i < labels.length; i++) labels[i] = names.get(i) + " — " + store.modVersion(names.get(i))
                + (store.isEnabled(names.get(i)) ? "" : " [disabled]");
            new AlertDialog.Builder(this).setTitle("Installed mods").setItems(labels, (d, which) -> modActions(names.get(which)))
                .setNegativeButton("Cancel", null).show();
        } catch (Exception e) { status(e.getMessage()); }
    }

    private void modActions(String name) {
        try {
            String repo = store.source(name).optString("repo", "");
            boolean enabled = store.isEnabled(name);
            String[] actions = {"Choose a different version", "Restore previous version", enabled ? "Disable mod" : "Enable mod"};
            new AlertDialog.Builder(this).setTitle(name).setItems(actions, (d, which) -> {
                if (which == 0) {
                    if (repo.isEmpty()) status("Imported ZIP: use Import a mod ZIP again with folder name " + name + ".");
                    else browseReleases(name, repo, 1);
                } else if (which == 1) job(() -> { store.rollback(name); return "Previous " + name + " restored."; });
                else job(() -> { store.toggleMod(name); return name + (store.isEnabled(name) ? " enabled." : " disabled."); });
            }).setNegativeButton("Cancel", null).show();
        } catch (Exception e) { status(e.getMessage()); }
    }

    @Override protected void onSaveInstanceState(Bundle state) {
        super.onSaveInstanceState(state); state.putString("zipName", zipName);
    }

    @Override public void onBackPressed() {
        if (!busy && pageReady) web.evaluateJavascript("window.LmmApp.back()", handled -> {
            if ("false".equals(handled)) super.onBackPressed();
        });
        else if (!busy) super.onBackPressed();
    }
    @Override protected void onResume() {
        super.onResume();
        if (pageReady && !busy) { refresh(); if (!gameRunning()) status(""); }
    }
    @Override protected void onDestroy() {
        worker.shutdown();
        if (web != null) { web.removeJavascriptInterface("LmmNative"); web.destroy(); web = null; }
        super.onDestroy();
    }
}
