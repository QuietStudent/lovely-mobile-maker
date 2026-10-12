package org.love2d.android;

import java.io.*;
import java.nio.file.*;
import java.util.zip.*;

public final class ArchiveStoreTest {
    static int checks;
    interface Action { void run() throws Exception; }
    static void expectFailure(Action action) throws Exception {
        try { action.run(); } catch (IOException expected) { checks++; return; }
        throw new AssertionError("Expected an IOException");
    }
    static void check(boolean passed) { if (!passed) throw new AssertionError(); checks++; }
    static File zip(File parent, String name, String... entries) throws Exception {
        File file = new File(parent, name);
        try (ZipOutputStream out = new ZipOutputStream(new FileOutputStream(file))) {
            for (String entry : entries) { out.putNextEntry(new ZipEntry(entry)); out.write("test".getBytes("UTF-8")); out.closeEntry(); }
        }
        return file;
    }
    static File dir(File root, String name, String value) throws Exception {
        File folder = new File(root, name); ArchiveStore.mkdir(folder);
        Files.write(new File(folder, "version").toPath(), value.getBytes("UTF-8")); return folder;
    }
    static String version(File folder) throws Exception { return new String(Files.readAllBytes(new File(folder, "version").toPath()), "UTF-8"); }

    public static void main(String[] args) throws Exception {
        File root = Files.createTempDirectory("lmm-updater-test").toFile();
        try {
            for (String name : new String[]{"../escape", "a/../../escape", "/absolute", "C:/escape", "a\\escape", "a/./file"})
                expectFailure(() -> ArchiveStore.extract(zip(root, "bad.zip", name), new File(root, "bad-stage")));
            check(!new File(root.getParentFile(), "escape").exists());
            File flat = new File(root, "flat");
            ArchiveStore.extract(zip(root, "flat.zip", "Multiplayer.json", "core.lua"), flat);
            check(ArchiveStore.modRoot(flat, "Multiplayer.json").equals(flat));
            File nested = new File(root, "nested");
            ArchiveStore.extract(zip(root, "nested.zip", "repo-tag/Multiplayer.json", "repo-tag/core.lua"), nested);
            check(ArchiveStore.modRoot(nested, "Multiplayer.json").getName().equals("repo-tag"));
            expectFailure(() -> ArchiveStore.modRoot(nested, "wrong.json"));
            expectFailure(() -> ArchiveStore.extract(zip(root, "empty.zip"), new File(root, "empty")));
            File target = dir(root, "Multiplayer", "old");
            File backup = new File(root, "backups/Multiplayer"); ArchiveStore.mkdir(backup.getParentFile());
            File next = dir(root, "stage-new", "new");
            ArchiveStore.install(next, target, backup);
            check(version(target).equals("new") && version(backup).equals("old"));
            ArchiveStore.rollback(target, backup);
            check(version(target).equals("old") && version(backup).equals("new"));
            ArchiveStore.rollback(target, backup);
            check(version(target).equals("new") && version(backup).equals("old"));
            // A failed activation must put the working installation back.
            expectFailure(() -> ArchiveStore.install(new File(root, "missing"), target, backup));
            check(version(target).equals("new"));
            // Recover a process that died after renaming the installation to its backup.
            check(target.renameTo(backup)); ArchiveStore.recover(target, backup);
            check(version(target).equals("new"));
            // Validate Android architecture and reject desktop/x86/ELF executables.
            byte[] elf = new byte[20]; elf[0]=127; elf[1]='E'; elf[2]='L'; elf[3]='F'; elf[4]=2; elf[5]=1; elf[16]=3; elf[18]=(byte)183;
            File library = new File(root, "library.so"); Files.write(library.toPath(), elf);
            ArchiveStore.checkElf(library, "arm64-v8a"); checks++;
            expectFailure(() -> ArchiveStore.checkElf(library, "armeabi-v7a"));
            elf[16]=2; Files.write(library.toPath(), elf); expectFailure(() -> ArchiveStore.checkElf(library, "arm64-v8a"));
            // CRC errors must be caught in staging.
            File corrupt = zip(root, "corrupt.zip", "file");
            byte[] corruptBytes = Files.readAllBytes(corrupt.toPath()); corruptBytes[34] ^= 1; Files.write(corrupt.toPath(), corruptBytes);
            expectFailure(() -> ArchiveStore.extract(corrupt, new File(root, "corrupt-stage")));
            // Bound expanded archives to protect the app's storage.
            File bomb = new File(root, "bomb.zip");
            try (ZipOutputStream out = new ZipOutputStream(new FileOutputStream(bomb))) {
                out.putNextEntry(new ZipEntry("large")); byte[] block = new byte[1024*1024];
                for (int i=0; i<257; i++) out.write(block); out.closeEntry();
            }
            expectFailure(() -> ArchiveStore.extract(bomb, new File(root, "bomb-stage")));
            System.out.println("Passed " + checks + " updater safety and rollback checks.");
        } finally { ArchiveStore.delete(root); }
    }
}
