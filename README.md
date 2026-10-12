# Lovely Mobile Maker

**[Download Lovely-Mobile-Maker.html](https://github.com/QuietStudent/lovely-mobile-maker/releases/latest/download/Lovely-Mobile-Maker.html)** · [Release page and instructions](https://github.com/QuietStudent/lovely-mobile-maker/releases/latest)

1. Open the downloaded file in Chrome or Edge.
2. Choose your `Balatro.exe` or `Balatro.love` file.
3. Select **Android** and **Lovely 0.9.0**.
4. Press **Ready**, then install the APK on your phone.

Use **Edit Mods** in the app to add mods or change their versions.

[Try the menu preview](https://github.com/QuietStudent/lovely-mobile-maker/releases/latest/download/Balatro-Launcher-Preview.html)

<details>
<summary>Developer information</summary>

# Development
Below are the instructions to make a working build.

## MBF zip

This uses a modified version of [ModsBeforeFriday](https://github.com/Lauriethefish/ModsBeforeFriday)'s [mbf-zip](https://github.com/Lauriethefish/ModsBeforeFriday/tree/main/mbf-zip) and [mbf-axml](https://github.com/Lauriethefish/ModsBeforeFriday/tree/main/mbf-axml) with support for WASM.

To build it you musr first install [wasm-pack](https://drager.github.io/wasm-pack/installer/). After that, you can run:

```sh
cd bindgen
RUSTFLAGS='--cfg getrandom_backend="wasm_js"' wasm-pack build --target web
```

This generates a folder pkg with the required files. There is a symlimk so this folder should be used automatically

## Base Apps
The base apk for android devices (base.apk) is built from [lmm-love-android](https://github.com/WilsontheWolf/lmm-love-android),
and the base ipa for iOS devices (base.ipa) is from [lmm-love](https://github.com/WilsontheWolf/lmm-love) (using [lmm-love-apple-dependencies](https://github.com/WilsontheWolf/lmm-love-apple-dependencies)).

If your goal is just to make changes to the website, you can just download the base apps from the production lmm ([base.apk](https://lmm.shorty.systems/base.apk) [base.ipa](https://lmm.shorty.systems/base.ipa)).


</details>
