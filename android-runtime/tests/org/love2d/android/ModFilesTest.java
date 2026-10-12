package org.love2d.android;

import java.io.*;
import java.nio.file.*;

public final class ModFilesTest {
    static int checks;
    interface Action { void run() throws Exception; }
    static void fails(Action action) throws Exception {
        try { action.run(); } catch (IOException expected) { checks++; return; }
        throw new AssertionError("Expected rejection");
    }
    static void check(boolean value) { if (!value) throw new AssertionError(); checks++; }
    static File touch(File root, String path) throws Exception {
        File file = new File(root, path); ArchiveStore.mkdir(file.getParentFile());
        Files.write(file.toPath(), "test".getBytes("UTF-8")); return file;
    }
    public static void main(String[] args) throws Exception {
        for (String bad : new String[]{"../escape", "a/b", "lovely", ".hidden", "", "name.swap", "a\\b"}) fails(() -> ModFiles.name(bad));
        check(ModFiles.name("JokerDisplay").equals("JokerDisplay"));
        check(ModFiles.repository("https://github.com/stupxd/Blueprint/releases/").equals("stupxd/Blueprint"));
        check(ModFiles.repository("Steamodded/smods").equals("Steamodded/smods"));
        check(ModFiles.repository("https://github.com/nh6574/JokerDisplay.git").equals("nh6574/JokerDisplay"));
        for (String bad : new String[]{"https://evil.example/mod", "https://github.com.evil.example/a/b", "https://user@github.com/a/b", "https://github.com:443/a/b", "../escape", "a/b/c", "a"})
            fails(() -> ModFiles.repository(bad));
        check(ModFiles.ref("1.0.0~BETA-0624a").equals("1.0.0~BETA-0624a"));
        check(ModFiles.ref("feature/a").equals("feature/a"));
        for (String bad : new String[]{"", "a b", "a\nb\nc", "a\u0000b"}) fails(() -> ModFiles.ref(bad));
        File root = Files.createTempDirectory("lmm-mod-import").toFile();
        try {
            File flat = new File(root, "flat"); touch(flat, "lovely.toml");
            check(ModFiles.root(flat).equals(flat));
            File wrapper = new File(root, "wrapped"); touch(wrapper, "mod-tag/main.lua");
            check(ModFiles.root(wrapper).getName().equals("mod-tag"));
            touch(wrapper, "second/main.lua"); fails(() -> ModFiles.root(wrapper));
            File missing = new File(root, "missing"); touch(missing, "README.md"); fails(() -> ModFiles.root(missing));
            File portable = new File(root, "portable"); touch(portable, "libs/nativefs.lua");
            ModFiles.checkPortable(portable); checks++;
            touch(portable, "libs/windows/injector.DLL"); fails(() -> ModFiles.checkPortable(portable));
            ByteArrayOutputStream copied = new ByteArrayOutputStream();
            ModFiles.copy(new ByteArrayInputStream(new byte[]{1, 2, 3}), copied); check(copied.size() == 3);
            InputStream huge = new InputStream() {
                public int read() { return 1; }
                public int read(byte[] buffer, int offset, int length) { return length; }
            };
            fails(() -> ModFiles.copy(huge, new OutputStream() { public void write(int b) {} public void write(byte[] b, int o, int n) {} }));
        } finally { ArchiveStore.delete(root); }
        System.out.println("ModFiles: " + checks + " checks passed");
    }
}
