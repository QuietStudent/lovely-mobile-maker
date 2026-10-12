package org.love2d.android;
import java.io.*;
import java.nio.file.*;
public final class ReleaseArchiveTest {
    public static void main(String[] args) throws Exception {
        File stage = Files.createTempDirectory("lmm-release-layout").toFile();
        try {
            ArchiveStore.extract(new File(args[0]), stage);
            File root = ArchiveStore.modRoot(stage, args[1]);
            if (!new File(root, args[2]).isFile()) throw new AssertionError("Release main file missing");
            System.out.println("Verified actual release archive: " + args[0]);
        } finally { ArchiveStore.delete(stage); }
    }
}
