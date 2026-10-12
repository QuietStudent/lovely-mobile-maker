package org.love2d.android;

import org.json.*;
import java.io.*;
import java.util.*;
import java.util.regex.*;

/** Reads installed mod descriptions without executing any Lua or changing files. */
public final class ModInventory {
    private static final int MAX_METADATA = 1024 * 1024;

    private static String read(File file) throws IOException {
        if (file.length() > MAX_METADATA) throw new IOException("Mod metadata is too large");
        try (InputStream in = new FileInputStream(file); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[4096]; int n;
            while ((n = in.read(buffer)) != -1) {
                if (out.size() + n > MAX_METADATA) throw new IOException("Mod metadata is too large");
                out.write(buffer, 0, n);
            }
            return out.toString("UTF-8");
        }
    }

    private static boolean inside(File folder, File child) throws IOException {
        return child.getCanonicalPath().startsWith(folder.getCanonicalPath() + File.separator);
    }

    private static File root(File folder, int depth) throws IOException {
        File[] files = folder.listFiles();
        if (files == null || files.length > 2048) return null;
        boolean hasLua = false, hasPatch = false, hasManaged = false;
        for (File file : files) {
            if (!inside(folder, file)) continue;
            String name = file.getName();
            if (file.isFile() && name.endsWith(".lua")) hasLua = true;
            if (name.equals("lovely.toml") && file.isFile()) hasPatch = true;
            if (name.equals(".lmm-release") && file.isFile()) hasManaged = true;
            if (name.equals("lovely") && file.isDirectory()) hasPatch = true;
        }
        if (hasLua || hasPatch || hasManaged) return folder;
        if (depth == 0) return null;
        File found = null;
        for (File file : files) {
            if (!file.isDirectory() || file.getName().startsWith(".") || !inside(folder, file)) continue;
            File candidate = root(file, depth - 1);
            if (candidate != null) {
                if (found != null) return null; // Do not treat several mods as one folder.
                found = candidate;
            }
        }
        return found;
    }

    public static JSONObject inspect(File folder) throws IOException {
        File root = root(folder, 2);
        if (root == null) return null;
        JSONObject info = new JSONObject();
        try {
            String id = "", title = folder.getName(), version = "", repo = "";
            JSONObject primary = null, fallback = null;
            File[] files = root.listFiles();
            if (files == null) return null;
            Arrays.sort(files, Comparator.comparing(File::getName));
            for (File file : files) {
                if (!file.isFile() || !file.getName().endsWith(".json") || !inside(root, file)) continue;
                try {
                    JSONObject metadata = new JSONObject(read(file));
                    if (metadata.has("id") && metadata.has("main_file")) {
                        File main = ArchiveStore.child(root, metadata.getString("main_file"));
                        if (main.isFile()) { if (primary == null) primary = metadata; }
                    } else if (metadata.has("name") && (metadata.has("version_number") || metadata.has("website_url"))) {
                        if (fallback == null) fallback = metadata;
                    }
                } catch (IOException | JSONException ignored) { }
            }
            if (primary != null) {
                id = primary.optString("id", "");
                title = primary.optString("display_name", primary.optString("name", title));
                version = primary.optString("version", "");
            }
            if (fallback != null) {
                if (primary == null) title = fallback.optString("name", title);
                if (version.isEmpty()) version = fallback.optString("version_number", "");
                try { repo = ModFiles.repository(fallback.optString("website_url", "")); } catch (IOException ignored) { }
            }
            boolean loader = new File(root, "lovely/loader.toml").isFile() && new File(root, "src").isDirectory();
            String shortcut = loader ? "Steamodded" : known(id);
            // Exact known folder names also support older patch-only mods with no JSON manifest.
            if (shortcut.isEmpty() && id.isEmpty()) shortcut = known(folder.getName());
            if (!shortcut.isEmpty()) {
                repo = RuntimeStore.repositoryFor(shortcut);
                if (shortcut.equals("Steamodded")) { id = "Steamodded"; title = "Steamodded"; }
                else if (shortcut.equals("Multiplayer")) title = "Balatro Multiplayer";
                else if (shortcut.equals("JokerDisplay")) title = "Joker Display";
                else if (shortcut.equals("WhatsInMyFool")) title = "What's In My Fool";
            }
            if (loader) {
                File versionFile = new File(root, "version.lua");
                if (versionFile.isFile() && inside(root, versionFile)) {
                    Matcher match = Pattern.compile("^\\s*return\\s+[\"']([^\"'\\r\\n]+)[\"']").matcher(read(versionFile));
                    if (match.find()) version = match.group(1);
                }
            }
            // Legacy Steamodded headers are data; never load or evaluate the mod.
            if (primary == null && !loader) for (File file : files) {
                if (!file.isFile() || !file.getName().endsWith(".lua") || !inside(root, file)) continue;
                if (file.length() > MAX_METADATA) continue;
                String header = read(file);
                if (!header.contains("--- STEAMODDED HEADER")) continue;
                for (String key : new String[]{"MOD_NAME", "MOD_ID", "VERSION"}) {
                    Matcher match = Pattern.compile("(?m)^---\\s*" + key + ":\\s*([^\\r\\n]+)").matcher(header);
                    if (match.find()) {
                        if (key.equals("MOD_NAME")) title = match.group(1).trim();
                        else if (key.equals("MOD_ID")) id = match.group(1).trim();
                        else if (version.isEmpty()) version = match.group(1).trim();
                    }
                }
                break;
            }
            if (repo.isEmpty() && !known(id).isEmpty()) repo = RuntimeStore.repositoryFor(known(id));
            info.put("id", id).put("title", title).put("version", version.isEmpty() ? "Version unknown" : version)
                .put("repo", repo).put("loader", loader).put("managed", new File(folder, ".lmm-release").isFile());
            return info;
        } catch (JSONException e) { throw new IOException("Cannot read mod description", e); }
    }

    private static String known(String id) {
        if (id.equalsIgnoreCase("Steamodded") || id.equalsIgnoreCase("smods")) return "Steamodded";
        if (id.equalsIgnoreCase("Multiplayer")) return "Multiplayer";
        if (id.equalsIgnoreCase("JokerDisplay")) return "JokerDisplay";
        if (id.equalsIgnoreCase("Blueprint")) return "Blueprint";
        if (id.equalsIgnoreCase("wimf") || id.equalsIgnoreCase("WhatsInMyFool")) return "WhatsInMyFool";
        return "";
    }
}
