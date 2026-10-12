// LMM extension to the upstream LÖVE Android activity.
package org.love2d.android;

import android.os.Bundle;
import android.system.Os;
import java.io.File;

/** Runs in a separate process so every launch gets a fresh Lovely runtime. */
public class ManagedGameActivity extends GameActivity {
    @Override protected void onCreate(Bundle state) {
        try {
            RuntimeStore store = new RuntimeStore(this);
            File library = store.selectedLibrary();
            Os.setenv("LMM_LOVELY_LIBRARY", library.getAbsolutePath(), true);
            Os.setenv("LOVELY_MOD_DIR", store.mods().getAbsolutePath(), true);
        } catch (Exception e) {
            // The launcher prepares and validates the library before opening this activity.
            throw new IllegalStateException("Open the LMM launcher and select a working Lovely version", e);
        }
        super.onCreate(state);
    }

    @Override protected void onDestroy() {
        super.onDestroy();
        // Lovely uses OnceCell globals; a new game must never reuse old patches/libraries.
        android.os.Process.killProcess(android.os.Process.myPid());
    }
}
