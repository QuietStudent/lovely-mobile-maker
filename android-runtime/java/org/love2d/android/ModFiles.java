package org.love2d.android;

import java.io.*;
import java.net.*;
import java.util.*;

/** Validation shared by the phone installer and host tests. */
public final class ModFiles {
    public static String name(String value) throws IOException {
        if (value == null || !value.matches("[A-Za-z0-9][A-Za-z0-9_.-]{0,79}")
                || value.equalsIgnoreCase("lovely") || value.endsWith(".swap")) throw new IOException("Use a mod folder name with letters, numbers, dots, underscores or hyphens (not lovely or a .swap name)");
        return value;
    }

    public static String repository(String value) throws IOException {
        String repo = value.trim();
        if (repo.startsWith("https://")) {
            try {
                URI uri = new URI(repo);
                if (!"github.com".equalsIgnoreCase(uri.getHost()) || uri.getUserInfo() != null || uri.getPort() != -1)
                    throw new IOException("Enter a github.com repository URL or owner/repo");
                String[] parts = uri.getPath().split("/");
                if (parts.length < 3) throw new IOException("Missing repository name");
                repo = parts[1] + "/" + parts[2];
            } catch (URISyntaxException e) { throw new IOException("Invalid repository URL", e); }
        }
        if (repo.endsWith(".git")) repo = repo.substring(0, repo.length() - 4);
        if (!repo.matches("[A-Za-z0-9][A-Za-z0-9-]*/[A-Za-z0-9][A-Za-z0-9_.-]*") || repo.length() > 180)
            throw new IOException("Enter owner/repo or its HTTPS GitHub URL");
        return repo;
    }

    public static String ref(String value) throws IOException {
        String ref = value.trim();
        if (ref.isEmpty() || ref.length() > 200)
            throw new IOException("Enter a release tag, branch name, or commit SHA");
        for (int i = 0; i < ref.length(); i++) if (Character.isWhitespace(ref.charAt(i)) || Character.isISOControl(ref.charAt(i)))
            throw new IOException("Version references cannot contain spaces or control characters");
        return ref;
    }

    public static void copy(InputStream in, OutputStream out) throws IOException {
        long total = 0; int n; byte[] buffer = new byte[32768];
        while ((n = in.read(buffer)) != -1) {
            if ((total += n) > 64L * 1024 * 1024) throw new IOException("Mod ZIP exceeds 64 MiB");
            out.write(buffer, 0, n);
        }
    }

    private static boolean marker(File root) {
        if (new File(root, "lovely.toml").isFile() || new File(root, "lovely").isDirectory()) return true;
        File[] lua = root.listFiles((dir, filename) -> filename.endsWith(".lua"));
        return lua != null && lua.length > 0;
    }

    /** Accept one mod, flat or with a single archive wrapper; never guess among modpacks. */
    public static File root(File stage) throws IOException {
        if (marker(stage)) return stage;
        File[] children = stage.listFiles();
        if (children != null && children.length == 1 && children[0].isDirectory() && marker(children[0]))
            return children[0];
        throw new IOException("Choose a ZIP containing one mod (Lua files or Lovely patches), not a modpack or installer");
    }

    public static void checkPortable(File root) throws IOException {
        File[] children = root.listFiles();
        if (children == null) throw new IOException("Cannot inspect mod files");
        for (File file : children) {
            if (file.isDirectory()) { checkPortable(file); continue; }
            String name = file.getName().toLowerCase(Locale.ROOT);
            if (name.endsWith(".dll") || name.endsWith(".exe") || name.endsWith(".so") || name.endsWith(".dylib"))
                throw new IOException("This ZIP contains native code (" + file.getName() + "); use a Lua/Lovely mod built for this runtime");
        }
    }
}
