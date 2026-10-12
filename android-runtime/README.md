# Android phone updater

This is an extension of Lovely Mobile Maker and its LÖVE Android runtime. It changes the generated Android app, including native LuaJIT code; editing the website alone cannot update Lovely inside an existing installation.

## Phone behavior

The app opens a bundled Balatro-themed launcher with exactly three home actions:
**Launch Game**, **Change Lovely Version**, and **Edit Mods**. Screens, styling,
fonts, installed version selection, enable/disable, and backups all run locally
inside the APK. The launcher opens in landscape to match the game, with three
side-by-side home buttons. No hosted dashboard, BMM backend, or server is required. Internet
is needed only to browse GitHub versions or download new mods/builds. ZIP imports
work with files already downloaded onto the phone.

The child screens preserve these features:

- A Lovely selector. **0.9.0 is the default**, and **0.10.0** is also bundled. These are Android libraries built from the actual upstream Lovely patch cores, not a change to the displayed version string.
- Release pickers for **Steamodded**, **Multiplayer**, **JokerDisplay** (`nh6574/JokerDisplay`), and **Blueprint** (`stupxd/Blueprint`). **What's In My Fool** (`colonthreeing/WhatsInMyFool`) has a Current source shortcut because it has no published releases: the contents of the repository source folder are installed directly in `Mods/WhatsInMyFool`, with the exact commit recorded. Older releases are paginated, prereleases are labeled, and an exact tag, branch, or commit can be entered. Steamodded is never changed automatically when installing another mod.
- A GitHub repository installer for other mods, and Android's document picker for importing a downloaded mod ZIP. Release ZIP assets can be chosen explicitly; otherwise the source archive at the selected tag is used. Branch installs resolve to a commit recorded in the installed version label.
- A managed-mod list with enable/disable and previous-version restore. Disabled folders live outside the game's Mods directory, so Lovely and Steamodded cannot load them. The launcher keeps Steamodded enabled because other mods depend on it.
- A game launch button. Every game runs in a fresh `:game` process so Lovely and mod patches are initialized again. Exit the game before changing versions or updating mods.
- An optional HTTPS catalog for future Android Lovely libraries published by a trusted maintainer.

The updater downloads Multiplayer's `BalatroMultiplayer-raw.zip` release asset when available, falling back to the selected release tag's source archive. Steamodded uses the selected release archive. Opening a release picker never downloads or replaces installed mod files. GitHub API limits or network failures appear on screen and can be retried.

Additional mods must be single-mod ZIPs containing Lua files or Lovely patches, either flat or inside one archive wrapper. Modpacks and native executables/libraries are rejected. JSON mod IDs and managed repository identities are checked to prevent common duplicate installations. Declared dependencies are displayed after installation but are not resolved automatically; choose compatible dependency versions yourself. Importing a local Steamodded replacement is intentionally excluded: use its dedicated version picker.

JokerDisplay's current documentation lists SMODS compatibility from `1.0.0~BETA-0624a`; check the chosen release's own requirements. Some older Steamodded releases use a different loader layout and are rejected rather than installed incorrectly. Arbitrary Lua/Lovely mods are not guaranteed to work with this Android runtime or Multiplayer. Keyboard-only mods may need an external keyboard, and native desktop plugins are unsupported.

Mods are installed in the runtime's existing private `files/save/ASET/Mods` directory. Saves and unrelated mod folders are preserved. Previous mod folders are kept in `files/lmm/backups` and can be restored with the launcher. Updating replaces the whole managed mod directory; any edits within that directory remain in its backup, not in the newly downloaded release.

If a mod is already installed under a different folder name or as an archive, move the old copy out of `Mods` first using the app's Android document storage. The updater detects common duplicate installations and stops instead of loading two copies.

## Build using GitHub Actions

1. Fork or clone this source, including `.github/workflows/android-mobile.yml`. Only source code belongs in the repository; keep `Balatro.exe` and `Balatro.love` on your own device.
2. Enable GitHub Actions if needed. Pushes changing the Android runtime or workflow trigger a build. When the workflow exists on the repository's default branch, **Actions → Android with phone updates → Run workflow** can also build a selected branch. The optional download-base input can be left empty.
3. Download the `lovely-mobile-maker-android` artifact after the build succeeds.
4. Extract the artifact and open **`Lovely-Mobile-Maker.html`** in Chrome or Edge on your laptop. This single file includes the new Android base and packaging engine. A copy made before the native build finishes instead requires selecting the workflow's `base.apk` in Step 2. The `web` directory is also included if you prefer to serve the maker as a static website.
5. Choose your local `Balatro.exe` or `Balatro.love`, select Android, confirm Lovely **0.9.0**, and press **Ready**. The maker downloads `game.apk`. Transfer it to the phone and install it. The game files are processed locally, not uploaded to GitHub.
6. Open the installed app, choose a Steamodded version and a Multiplayer version, then launch the game. Use the JokerDisplay/Blueprint shortcuts or the GitHub/ZIP installers for additional mods. Later mod updates and switches between the bundled Lovely versions happen in that launcher.

Android file managers vary in how they open local HTML. The generated APK's launcher handles ongoing updates directly on the phone; the single HTML file is also usable through the included static web bundle if the phone browser refuses to execute a local HTML file. GitHub Actions can be started from a phone browser.

The workflow does not publish a website or release automatically. The base APK contains no Balatro files and is not a ready-to-play Balatro APK.

An existing LMM installation needs **one installation of an APK generated with this new base**. Retain the same bundle ID and maker signing certificate, install it over the old app, and do not uninstall first. Subsequent bundled Lovely changes and supported mod updates happen on the phone. Existing installations keep their saved Lovely choice when an APK is updated.

## Linux build

Prerequisites: JDK 17, Node 22, stable Rust with `aarch64-linux-android` and `armv7-linux-androideabi` targets, Android SDK platform 34, build tools 34, NDK `25.2.9519653`, GCC with 32-bit host support, make, and host LuaJIT. `ANDROID_SDK_ROOT` must identify the SDK.

```sh
bash android-runtime/test.sh
bash android-runtime/build.sh
cp dist/base.apk base.apk
cd bindgen
RUSTFLAGS='--cfg getrandom_backend="wasm_js"' wasm-pack build --target web
```

Use a fresh `.build/android` checkout when changing the overlay. The overlay checks exact source matches and refuses to silently patch a different upstream layout. The build script retains its checkouts for retrying failed builds.

Pinned sources:

- Android runtime: `WilsontheWolf/lmm-love-android` at `c36e6685723bf4b85f04cf554a650af400fc50e0`, including its recorded LÖVE and HTTPS submodules.
- Lovely 0.9.0: `ethangreen-dev/lovely-injector` at `91759da5702618c3b940fbbe8135954414c0ef34`.
- Lovely 0.10.0: `ethangreen-dev/lovely-injector` at `073060eeb81b3664f476bdd33c263663d48762d1`.

The builder compiles the patch core as an Android shared library and rebuilds LuaJIT with the mobile bridge. It replaces the prebuilt LuaJIT archives that previously linked Lovely statically. This build targets ARM64 and ARMv7, Android 7.0/API 24 or later because the web maker uses APK v2 signing. Desktop/x86 libraries are rejected. The requester reports that the generated app is working on their Android 16 / MediaTek Dimensity 8300 phone.

## Publishing additional Lovely versions

The launcher does not assume desktop Lovely releases have Android assets. A maintainer must build Android libraries implementing **mobile API 1**, publish them over HTTPS, and publish this catalog shape:

```json
{
  "mobile_api": 1,
  "releases": [
    {
      "version": "0.9.0",
      "libraries": {
        "arm64-v8a": { "url": "https://YOUR-HOST/lovely/0.9.0/arm64-v8a/liblovely.so", "sha256": "64_HEX_CHARACTERS" },
        "armeabi-v7a": { "url": "https://YOUR-HOST/lovely/0.9.0/armeabi-v7a/liblovely.so", "sha256": "64_HEX_CHARACTERS" }
      }
    }
  ]
}
```

These are schema placeholders, not working download URLs. Set `LOVELY_DOWNLOAD_BASE` during a build to generate `dist/lovely-catalog.json` using actual library hashes. Upload the generated `lovely` directory and catalog to that HTTPS directory, then enter the catalog URL in the phone launcher. Version paths are immutable; publish a different version instead of replacing an installed version in place.

The mobile library exports `lovely_mobile_abi() == 1`, `lovely_init`, and `lovely_apply_patches`, with the `LuaLib` layout in `native/lovely.h`. A future upstream change to that layout needs a bridge/API update, not just a version number added to the catalog. Checksums detect corrupted downloads; trust in executable code still comes from the chosen catalog maintainer and HTTPS source.

## Verification and limits

66 host checks cover unsafe ZIP paths, CRC errors, extraction limits, ELF architecture, transactional installation, rollback, interrupted-install recovery, HTTPS-only redirects, status errors, size limits, cancellation, SHA-256, and mod repository/ref/import validation. A separate 35-check integration harness verified actual JokerDisplay and Blueprint archives, an exact older Steamodded release, Blueprint's release ZIP asset, branch-to-commit installation, unchanged dependencies, disabled-mod update/restore/recovery, and provenance files that Steamodded cannot mistake for mod manifests. Java compilation was checked against Android API 34 and the existing LÖVE/SDL classes.

The complete Gradle/native Android build and web WASM build passed during development, compiling Lovely 0.9.0 and 0.10.0 for ARM64 and ARMv7. The tested APK's final Java-only provenance-filename fix passed Java compilation and was applied to its optimized DEX before assembly with Android's D8 tool, preserving its classes and native libraries. Full builds from this source include that fix directly. The requester reports successful testing on their Android 16 / MediaTek Dimensity 8300 phone. This report covers one device; compatibility across other phones and mod combinations needs further testing. Multiplayer requires compatible Balatro files and compatible versions across players.

## Licenses

The maker remains AGPL-3.0. LÖVE and LuaJIT retain their upstream license notices; Lovely's patch core is MIT. The bridge header follows upstream `liblovely`'s public C layout. Builds fetch upstream source and preserve its notices. Balatro files are supplied by the user and are not redistributed by this repository or workflow.
