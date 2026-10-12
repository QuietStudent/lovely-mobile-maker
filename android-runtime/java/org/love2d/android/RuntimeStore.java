package org.love2d.android;

import android.content.Context;
import android.os.Build;
import org.json.*;
import java.io.*;
import java.net.URLEncoder;
import java.util.*;

public final class RuntimeStore {
    private final Context context;
    private final File libraries;
    private final File backups;
    private final File disabled;
    public final String abi;
    public static final String DEFAULT_VERSION = "0.9.0";

    public RuntimeStore(Context context) throws IOException {
        this.context = context;
        libraries = new File(context.getFilesDir(), "lmm/lovely");
        backups = new File(context.getFilesDir(), "lmm/backups");
        disabled = new File(context.getFilesDir(), "lmm/disabled-mods");
        ArchiveStore.mkdir(libraries); ArchiveStore.mkdir(backups); ArchiveStore.mkdir(disabled);
        String found = null;
        for (String candidate : Build.SUPPORTED_ABIS) {
            if (candidate.equals("arm64-v8a") || candidate.equals("armeabi-v7a")) { found = candidate; break; }
        }
        if (found == null) throw new IOException("This build supports ARM Android devices only");
        abi = found;
        if (!context.getSharedPreferences("lmm", 0).contains("lovely")) {
            String initial = DEFAULT_VERSION;
            try (InputStream in = context.getAssets().open("lmm/settings.json"); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
                byte[] buf = new byte[1024]; int n;
                while ((n = in.read(buf)) != -1) out.write(buf, 0, n);
                initial = new JSONObject(out.toString("UTF-8")).getString("lovely");
                library(initial);
            } catch (Exception ignored) { initial = DEFAULT_VERSION; }
            if (!context.getSharedPreferences("lmm", 0).edit().putString("lovely", initial).commit())
                throw new IOException("Cannot save initial Lovely version");
        }
    }

    public File mods() throws IOException {
        File mods = new File(context.getFilesDir(), "save/ASET/Mods");
        ArchiveStore.mkdir(mods);
        return mods;
    }

    public String selected() {
        return context.getSharedPreferences("lmm", 0).getString("lovely", DEFAULT_VERSION);
    }

    private File library(String version) throws IOException {
        if (!version.matches("[0-9]+\\.[0-9]+\\.[0-9]+(?:-[A-Za-z0-9.-]+)?")) throw new IOException("Invalid Lovely version");
        return new File(libraries, version + "/" + abi + "/liblovely.so");
    }

    public File selectedLibrary() throws IOException {
        File lib = library(selected());
        if (!lib.isFile()) throw new IOException("Selected Lovely is not installed");
        ArchiveStore.checkElf(lib, abi);
        if (lib.canWrite()) throw new IOException("Lovely library must be read-only");
        return lib;
    }

    public List<String> versions() throws IOException {
        Set<String> names = new TreeSet<>();
        String[] bundled = context.getAssets().list("lmm/lovely");
        if (bundled != null) Collections.addAll(names, bundled);
        String[] installed = libraries.list();
        if (installed != null) Collections.addAll(names, installed);
        return new ArrayList<>(names);
    }

    public void select(String version) throws Exception {
        File dest = library(version);
        if (!dest.isFile()) {
            File temp = new File(context.getFilesDir(), "lmm/library-download.tmp");
            if (temp.exists()) { temp.setWritable(true); ArchiveStore.delete(temp); }
            try (InputStream in = context.getAssets().open("lmm/lovely/" + version + "/" + abi + "/liblovely.so");
                 FileOutputStream out = new FileOutputStream(temp)) {
                if (!temp.setReadOnly()) throw new IOException("Cannot protect Lovely library");
                byte[] buf = new byte[32768]; int n;
                while ((n = in.read(buf)) != -1) out.write(buf, 0, n);
                out.getFD().sync();
            } catch (Exception e) { temp.delete(); throw e; }
            ArchiveStore.checkElf(temp, abi);
            ArchiveStore.mkdir(dest.getParentFile());
            if (!temp.renameTo(dest)) throw new IOException("Cannot install bundled Lovely");
        }
        ArchiveStore.checkElf(dest, abi);
        if (!context.getSharedPreferences("lmm", 0).edit().putString("lovely", version).commit())
            throw new IOException("Cannot save Lovely selection");
    }

    public void recoverMods() throws Exception {
        Set<String> names = new HashSet<>(Arrays.asList("Multiplayer", "Steamodded"));
        names.addAll(managedMods());
        for (String key : context.getSharedPreferences("lmm", 0).getAll().keySet())
            if (key.startsWith("disabled.")) names.add(key.substring("disabled.".length()));
        File[] saved = backups.listFiles();
        if (saved != null) for (File file : saved) {
            String name = file.getName();
            names.add(name.endsWith(".swap") ? name.substring(0, name.length() - 5) : name);
        }
        for (String name : names) {
            ModFiles.name(name);
            File target = modLocation(name);
            File alternate = isEnabled(name) ? new File(disabled, name) : new File(mods(), name);
            if (!target.exists() && alternate.exists() && !alternate.renameTo(target))
                throw new IOException("Cannot recover mod state for " + name);
            ArchiveStore.recover(target, new File(backups, name + ".swap"));
            ArchiveStore.recover(target, new File(backups, name));
        }
    }

    /** Detect existing manually installed copies before introducing a duplicate mod ID. */
    private void checkDuplicate(String name, String id) throws Exception {
        File[] entries = mods().listFiles();
        if (entries == null) throw new IOException("Cannot list mods");
        for (File file : entries) {
            if (file.getName().equals(name) || file.getName().equals("lovely")) continue;
            if (!id.isEmpty() && file.isDirectory()) {
                File[] metadata = file.listFiles((dir, filename) -> filename.endsWith(".json"));
                if (metadata != null) for (File json : metadata) {
                    if (json.length() > 1024 * 1024) continue;
                    try {
                        JSONObject mod = new JSONObject(read(json));
                        if (mod.optString("id").equals(id))
                            throw new IOException("Another " + id + " copy is in Mods/" + file.getName()
                                + ". Move it out using the app's document storage before updating.");
                    } catch (JSONException ignored) { }
                }
            }
            if (name.equals("Steamodded") && file.isDirectory()
                && new File(file, "lovely/loader.toml").isFile())
                throw new IOException("Another Steamodded copy is in Mods/" + file.getName() + ". Move it out first.");
            if (file.getName().toLowerCase(Locale.ROOT).endsWith(".zip")
                    && file.getName().toLowerCase(Locale.ROOT).contains(name.toLowerCase(Locale.ROOT)))
                throw new IOException("Move the old mod archive " + file.getName() + " out of Mods first.");
        }
    }

    public String updateMod(String name) throws Exception {
        String repo = repositoryFor(name);
        JSONObject release = new JSONObject(Downloads.json(api(repo) + "/releases/latest"));
        return installRelease(name, repo, release);
    }

    public static String repositoryFor(String name) throws IOException {
        if (name.equals("Multiplayer")) return "Balatro-Multiplayer/BalatroMultiplayer";
        if (name.equals("Steamodded")) return "Steamodded/smods";
        if (name.equals("JokerDisplay")) return "nh6574/JokerDisplay";
        if (name.equals("Blueprint")) return "stupxd/Blueprint";
        throw new IOException("Unknown shortcut");
    }

    private static String api(String repo) throws IOException {
        return "https://api.github.com/repos/" + ModFiles.repository(repo);
    }

    /** Releases include prereleases; page numbers allow browsing the full history. */
    public List<JSONObject> releases(String repo, int page) throws Exception {
        if (page < 1) throw new IOException("Invalid release page");
        JSONArray response = new JSONArray(Downloads.json(api(repo) + "/releases?per_page=30&page=" + page));
        List<JSONObject> result = new ArrayList<>();
        for (int i = 0; i < response.length(); i++) {
            JSONObject release = response.getJSONObject(i);
            if (!release.optBoolean("draft")) result.add(release);
        }
        return result;
    }

    public String installRelease(String name, String repo, JSONObject release) throws Exception {
        String tag = release.getString("tag_name");
        if (release.optBoolean("draft")) throw new IOException("Draft releases cannot be installed");
        String url = api(repo) + "/zipball/" + URLEncoder.encode(ModFiles.ref(tag), "UTF-8");
        String expectedDigest = "";
        // The raw release contains source files without a bundled competing Steamodded copy.
        if (repo.equalsIgnoreCase(repositoryFor("Multiplayer"))) {
            JSONArray assets = release.optJSONArray("assets");
            if (assets != null) for (int i = 0; i < assets.length(); i++) {
                JSONObject asset = assets.getJSONObject(i);
                if (asset.getString("name").equals("BalatroMultiplayer-raw.zip")) {
                    url = asset.getString("browser_download_url");
                    expectedDigest = asset.optString("digest", "");
                }
            }
        }
        return downloadMod(name, repo, tag, url, expectedDigest);
    }

    public String installRef(String name, String repo, String ref) throws Exception {
        repo = ModFiles.repository(repo); ref = ModFiles.ref(ref);
        // Pin branches to their resolved commit so the installed provenance remains useful.
        JSONObject commit = new JSONObject(Downloads.json(api(repo) + "/commits/" + URLEncoder.encode(ref, "UTF-8")));
        String sha = commit.getString("sha");
        if (!sha.matches("[a-fA-F0-9]{40}")) throw new IOException("Invalid GitHub commit");
        return downloadMod(name, repo, ref + " @ " + sha.substring(0, 12), api(repo) + "/zipball/" + sha, "");
    }

    public String installAsset(String name, String repo, JSONObject release, JSONObject asset) throws Exception {
        String filename = asset.getString("name");
        if (!filename.toLowerCase(Locale.ROOT).endsWith(".zip")) throw new IOException("Choose a ZIP release asset");
        return downloadMod(name, repo, release.getString("tag_name"), asset.getString("browser_download_url"), asset.optString("digest", ""));
    }

    private String downloadMod(String name, String repo, String version, String url, String digest) throws Exception {
        File work = new File(context.getFilesDir(), "lmm/mod-stage");
        ArchiveStore.delete(work); ArchiveStore.mkdir(work);
        try {
            File zip = new File(work, "download.zip");
            try (FileOutputStream out = new FileOutputStream(zip)) { Downloads.fetch(url, out, 64L * 1024 * 1024); out.getFD().sync(); }
            if (digest.startsWith("sha256:") && !Downloads.sha256(zip).equalsIgnoreCase(digest.substring(7)))
                throw new IOException("Release checksum does not match");
            return installArchive(zip, work, name, version, repo);
        } finally { ArchiveStore.delete(work); }
    }

    public String importZip(InputStream input, String name) throws Exception {
        File work = new File(context.getFilesDir(), "lmm/mod-stage");
        ArchiveStore.delete(work); ArchiveStore.mkdir(work);
        try {
            File zip = new File(work, "import.zip");
            try (InputStream in = input; FileOutputStream out = new FileOutputStream(zip)) { ModFiles.copy(in, out); out.getFD().sync(); }
            return installArchive(zip, work, name, "local ZIP", "");
        } finally { ArchiveStore.delete(work); }
    }

    private String installArchive(File zip, File work, String name, String version, String repo) throws Exception {
        ModFiles.name(name); recoverMods();
        if (repo.equalsIgnoreCase(repositoryFor("Steamodded")) && !name.equals("Steamodded"))
            throw new IOException("Install this loader using folder name Steamodded to prevent duplicate loaders");
        File extracted = new File(work, "extracted"); ArchiveStore.extract(zip, extracted);
        File root;
        if (name.equals("Steamodded")) {
            if (!repo.equalsIgnoreCase(repositoryFor(name))) throw new IOException("Use the Steamodded shortcut to replace the loader");
            root = ArchiveStore.modRoot(extracted, "manifest.json");
            if (!new File(root, "lovely").isDirectory() || !new File(root, "src").isDirectory())
                throw new IOException("This Steamodded archive uses an unsupported loader layout");
        } else {
            root = ModFiles.root(extracted);
            if (new File(root, "lovely/loader.toml").isFile() && new File(root, "src").isDirectory())
                throw new IOException("Use the Steamodded version picker to install this loader");
        }
        ModFiles.checkPortable(root);
        String id = "", dependencies = "";
        File[] json = root.listFiles((dir, filename) -> filename.endsWith(".json"));
        if (json != null) for (File file : json) {
            JSONObject metadata;
            try { metadata = new JSONObject(read(file)); } catch (JSONException ignored) { continue; }
            if (!metadata.has("id") || !metadata.has("main_file")) continue;
            if (!id.isEmpty()) throw new IOException("Archive contains multiple mod manifests; select one mod ZIP");
            id = metadata.getString("id");
            if (!ArchiveStore.child(root, metadata.getString("main_file")).isFile()) throw new IOException("Mod's main Lua file is missing");
            if (metadata.has("dependencies")) dependencies = "\nDeclared dependencies: " + metadata.get("dependencies").toString();
        }
        if (name.equals("Multiplayer") && (!id.equals("Multiplayer") || !new File(root, "core.lua").isFile()))
            throw new IOException("Invalid Multiplayer mod archive");
        checkDuplicate(name, id);
        for (String other : managedMods()) {
            if (other.equals(name)) continue;
            JSONObject metadata = source(other);
            if ((!id.isEmpty() && id.equals(metadata.optString("id"))) || (!repo.isEmpty() && repo.equalsIgnoreCase(metadata.optString("repo"))))
                throw new IOException("This mod is already managed as " + other + ". Update that entry instead.");
        }
        JSONObject metadata = new JSONObject().put("repo", repo).put("version", version).put("id", id);
        // Steamodded scans every *.json file in mod folders; provenance must not look like a mod manifest.
        try (FileOutputStream out = new FileOutputStream(new File(root, ".lmm-source"))) { out.write(metadata.toString().getBytes("UTF-8")); }
        try (FileOutputStream out = new FileOutputStream(new File(root, ".lmm-release"))) { out.write(version.getBytes("UTF-8")); }
        ArchiveStore.install(root, modLocation(name), new File(backups, name));
        return name + " installed: " + version + ". Previous files are backed up."
            + dependencies + "\nDependencies were not changed automatically.";
    }

    private File modLocation(String name) throws IOException {
        ModFiles.name(name);
        return isEnabled(name) ? new File(mods(), name) : new File(disabled, name);
    }

    public List<String> managedMods() throws IOException {
        Set<String> names = new TreeSet<>();
        for (File parent : new File[]{mods(), disabled}) {
            File[] children = parent.listFiles();
            if (children == null) throw new IOException("Cannot list installed mods");
            for (File child : children) if (new File(child, ".lmm-release").isFile()) names.add(child.getName());
        }
        return new ArrayList<>(names);
    }

    public boolean isEnabled(String name) throws IOException {
        ModFiles.name(name);
        return !context.getSharedPreferences("lmm", 0).getBoolean("disabled." + name, false);
    }

    public JSONObject source(String name) throws Exception {
        File file = new File(modLocation(name), ".lmm-source");
        if (file.isFile()) return new JSONObject(read(file));
        JSONObject result = new JSONObject();
        try { result.put("repo", repositoryFor(name)); } catch (IOException ignored) { }
        return result;
    }

    public void toggleMod(String name) throws Exception {
        recoverMods();
        if (name.equals("Steamodded")) throw new IOException("Keep Steamodded enabled; other mods depend on the loader");
        boolean wasEnabled = isEnabled(name);
        File original = modLocation(name);
        File target = wasEnabled ? new File(disabled, name) : new File(mods(), name);
        if (!original.exists() || target.exists()) throw new IOException("Cannot change mod state");
        if (!context.getSharedPreferences("lmm", 0).edit().putBoolean("disabled." + name, wasEnabled).commit())
            throw new IOException("Cannot save mod state");
        // A crash after recording the state is recovered by moving the alternate folder at startup.
        if (!original.renameTo(target)) {
            context.getSharedPreferences("lmm", 0).edit().putBoolean("disabled." + name, !wasEnabled).commit();
            throw new IOException("Cannot change mod state");
        }
    }

    public String modVersion(String name) {
        try { return read(new File(modLocation(name), ".lmm-release")); }
        catch (Exception e) { return "not managed by LMM"; }
    }

    public void rollback(String name) throws Exception {
        recoverMods(); ArchiveStore.rollback(modLocation(name), new File(backups, name));
    }

    /** A maintainer-hosted catalog can publish future Android builds without rebuilding the APK. */
    public List<String> onlineVersions(String catalogUrl) throws Exception {
        JSONObject catalog = new JSONObject(Downloads.json(catalogUrl));
        if (catalog.getInt("mobile_api") != 1) throw new IOException("Unsupported mobile catalog API");
        JSONArray releases = catalog.getJSONArray("releases");
        List<String> versions = new ArrayList<>();
        for (int i = 0; i < releases.length(); i++) {
            JSONObject release = releases.getJSONObject(i);
            String version = release.getString("version");
            library(version); // validate before rendering it
            if (release.getJSONObject("libraries").has(abi)) versions.add(version);
        }
        return versions;
    }

    public void downloadLovely(String catalogUrl, String version) throws Exception {
        JSONObject catalog = new JSONObject(Downloads.json(catalogUrl));
        if (catalog.getInt("mobile_api") != 1) throw new IOException("Unsupported mobile catalog API");
        JSONArray releases = catalog.getJSONArray("releases");
        for (int i = 0; i < releases.length(); i++) {
            JSONObject release = releases.getJSONObject(i);
            if (!release.getString("version").equals(version)) continue;
            JSONObject artifact = release.getJSONObject("libraries").getJSONObject(abi);
            String hash = artifact.getString("sha256");
            if (!hash.matches("[a-fA-F0-9]{64}")) throw new IOException("Catalog has an invalid SHA-256");
            File dest = library(version);
            // Version paths are immutable. Restarting can safely use an already installed copy.
            if (dest.isFile()) { select(version); return; }
            File temp = new File(libraries, "download.tmp");
            if (temp.exists()) { temp.setWritable(true); ArchiveStore.delete(temp); }
            try {
                try (FileOutputStream out = new FileOutputStream(temp)) {
                    if (!temp.setReadOnly()) throw new IOException("Cannot protect library");
                    Downloads.fetch(artifact.getString("url"), out, 32L * 1024 * 1024); out.getFD().sync();
                }
                if (!Downloads.sha256(temp).equalsIgnoreCase(hash)) throw new IOException("Lovely checksum does not match");
                ArchiveStore.checkElf(temp, abi);
                ArchiveStore.mkdir(dest.getParentFile());
                if (!temp.renameTo(dest)) throw new IOException("Cannot activate downloaded Lovely");
                select(version);
            } finally { temp.delete(); }
            return;
        }
        throw new IOException("Version no longer exists in the catalog");
    }

    private static String read(File file) throws IOException {
        if (file.length() > 1024 * 1024) throw new IOException("Metadata exceeds size limit");
        try (InputStream in = new FileInputStream(file); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            byte[] buf = new byte[4096]; int n;
            while ((n = in.read(buf)) != -1) out.write(buf, 0, n);
            return out.toString("UTF-8").trim();
        }
    }
}
