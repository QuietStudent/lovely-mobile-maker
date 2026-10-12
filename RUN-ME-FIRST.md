# Build and use the Android phone updater

The Android launcher defaults to actual Lovely **0.9.0**, with **0.10.0** also bundled. It supports Steamodded and Multiplayer release selection, JokerDisplay and Blueprint shortcuts, GitHub and local ZIP mod installs, and previous-version restore. Installing another mod leaves dependency versions unchanged.

## Build the Android base and HTML maker

The source includes `.github/workflows/android-mobile.yml`. With GitHub Actions enabled, pushes changing `android-runtime` or the workflow trigger a build. Manual **Run workflow** is available once the workflow is present on the repository's default branch; choose the branch to build. A contribution branch does not replace the fork's default branch.

When the **Android with phone updates** run succeeds, download and extract the **lovely-mobile-maker-android** artifact. It contains `base.apk`, the Android Lovely libraries, the web maker, and a standalone `Lovely-Mobile-Maker.html`. Supply your own Balatro game files locally; they are not included in the repository or workflow.

For a local Linux build, see `android-runtime/README.md` for prerequisites and pinned source revisions.

## Generate your game APK

1. Open the built **Lovely-Mobile-Maker.html** in Chrome or Edge.
2. Select your own `Balatro.exe` or `Balatro.love`.
3. Choose **Android**. The standalone HTML includes the updated base. If using the separate web maker, supply the built `base.apk` in Step 2.
4. Confirm **Lovely 0.9.0** and press **Ready**. The maker creates `game.apk` locally.
5. Transfer the APK to your phone. When updating an existing installation, retain the same package ID and signing certificate and install over it to keep saves and mods.
6. Open the launcher, choose Steamodded and Multiplayer versions, add other mods as needed, and launch the game.

Later supported mod updates and bundled Lovely changes happen directly in the Android launcher. Future Lovely versions require Android libraries and a compatible HTTPS catalog published by a maintainer.

## Verification

The native Android and web WASM builds succeeded during development. The host suite contains 66 checks; an additional installer integration harness passed 35 checks with actual release archives. The APK signature and packaged game files were verified locally.

The requester reports that the generated app is working on their Android 16 / MediaTek Dimensity 8300 phone. This is a report from one device, and does not establish compatibility with every phone or mod combination.

The native runtime overlay and build instructions are in `android-runtime/README.md`. Existing production bases require a rebuild to include the launcher and dynamic Lovely bridge.
