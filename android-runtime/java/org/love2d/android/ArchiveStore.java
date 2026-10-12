package org.love2d.android;

import java.io.*;
import java.util.*;
import java.util.zip.*;

/** Plain Java file operations, shared by Android and the host regression tests. */
public final class ArchiveStore {
    private static final long MAX_EXPANDED = 256L * 1024 * 1024;
    private static final int MAX_FILES = 20000;

    public static void mkdir(File dir) throws IOException {
        if (!dir.isDirectory() && !dir.mkdirs()) throw new IOException("Cannot create " + dir);
    }

    public static void delete(File path) throws IOException {
        if (!path.exists()) return;
        if (path.isDirectory()) {
            File[] children = path.listFiles();
            if (children == null) throw new IOException("Cannot list " + path);
            for (File child : children) delete(child);
        }
        if (!path.delete()) throw new IOException("Cannot remove " + path);
    }

    public static File child(File root, String name) throws IOException {
        if (name.isEmpty() || name.contains("\\") || name.startsWith("/") || name.contains(":"))
            throw new IOException("Unsafe archive path: " + name);
        for (String part : name.split("/")) {
            if (part.equals("..") || part.equals(".")) throw new IOException("Unsafe archive path: " + name);
        }
        File target = new File(root, name).getCanonicalFile();
        if (!target.getPath().startsWith(root.getCanonicalPath() + File.separator))
            throw new IOException("Archive entry escapes its destination");
        return target;
    }

    public static void extract(File archive, File stage) throws IOException {
        // Require a complete central directory, not just a stream of local ZIP entries.
        try (ZipFile validated = new ZipFile(archive)) {
            if (validated.size() == 0 || validated.size() > MAX_FILES) throw new IOException("Invalid archive entry count");
        }
        mkdir(stage);
        Set<String> names = new HashSet<>();
        long total = 0;
        int count = 0;
        try (ZipInputStream zip = new ZipInputStream(new FileInputStream(archive))) {
            ZipEntry entry;
            byte[] buffer = new byte[32768];
            while ((entry = zip.getNextEntry()) != null) {
                if (++count > MAX_FILES) throw new IOException("Too many archive entries");
                File dest = child(stage, entry.getName());
                if (!names.add(dest.getPath())) throw new IOException("Duplicate archive entry");
                if (entry.isDirectory()) { mkdir(dest); continue; }
                mkdir(dest.getParentFile());
                try (OutputStream out = new FileOutputStream(dest)) {
                    int n;
                    while ((n = zip.read(buffer)) != -1) {
                        total += n;
                        if (total > MAX_EXPANDED) throw new IOException("Archive exceeds extraction limit");
                        out.write(buffer, 0, n);
                    }
                }
                zip.closeEntry(); // checks the CRC before any installed files are replaced
            }
        }
        if (count == 0) throw new IOException("Empty or invalid ZIP");
    }

    /** Accept a flat release zip or exactly one GitHub archive wrapper directory. */
    public static File modRoot(File stage, String marker) throws IOException {
        if (new File(stage, marker).isFile()) return stage;
        File[] children = stage.listFiles();
        if (children != null && children.length == 1 && children[0].isDirectory()
                && new File(children[0], marker).isFile()) return children[0];
        throw new IOException("Archive is missing " + marker + " at its mod root");
    }

    /** Recover a swap interrupted after saving the previous installation. */
    public static void recover(File target, File backup) throws IOException {
        if (!target.exists() && backup.exists() && !backup.renameTo(target))
            throw new IOException("Cannot recover " + target.getName());
    }

    public static void install(File source, File target, File backup) throws IOException {
        mkdir(target.getParentFile());
        recover(target, backup);
        delete(backup);
        boolean hadOld = target.exists();
        if (hadOld && !target.renameTo(backup)) throw new IOException("Cannot back up " + target.getName());
        if (!source.renameTo(target)) {
            if (hadOld && !backup.renameTo(target)) throw new IOException("Install and recovery failed; backup: " + backup);
            throw new IOException("Cannot activate " + target.getName());
        }
    }

    public static void rollback(File target, File backup) throws IOException {
        if (!backup.exists()) throw new IOException("No previous version to restore");
        File swap = new File(backup.getParentFile(), backup.getName() + ".swap");
        recover(target, swap);
        delete(swap);
        if (!target.renameTo(swap)) throw new IOException("Cannot save current version");
        if (!backup.renameTo(target)) {
            if (!swap.renameTo(target)) throw new IOException("Rollback recovery failed; backup: " + swap);
            throw new IOException("Cannot restore previous version");
        }
        if (!swap.renameTo(backup)) throw new IOException("Previous version restored; cannot retain current backup");
    }

    public static void checkElf(File library, String abi) throws IOException {
        byte[] header = new byte[20];
        try (DataInputStream in = new DataInputStream(new FileInputStream(library))) { in.readFully(header); }
        int machine = (header[18] & 255) | ((header[19] & 255) << 8);
        int expected = abi.equals("arm64-v8a") ? 183 : abi.equals("armeabi-v7a") ? 40 : -1;
        int elfClass = abi.equals("arm64-v8a") ? 2 : 1;
        if (header[0] != 127 || header[1] != 'E' || header[2] != 'L' || header[3] != 'F'
            || header[4] != elfClass || header[5] != 1 || header[16] != 3 || header[17] != 0 || machine != expected)
            throw new IOException("Expected an Android shared library for " + abi);
    }
}
