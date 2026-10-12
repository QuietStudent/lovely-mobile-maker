param([string[]]$Versions = @('0.9.0','0.10.0'), [string[]]$Abis = @('arm64-v8a','armeabi-v7a'))
$ErrorActionPreference = 'Stop'
$taskRoot = Split-Path -Parent $PSScriptRoot
$taskBuild = Join-Path $taskRoot '.build'
$taskAndroid = Join-Path $taskRoot 'android'
$env:CARGO_HOME = Join-Path $taskBuild 'cargo'
$env:RUSTUP_HOME = Join-Path $taskBuild 'rustup'
$env:GRADLE_USER_HOME = Join-Path $taskBuild 'gradle'
$env:ANDROID_SDK_ROOT = Join-Path $taskBuild 'sdk'
$env:ANDROID_HOME = $env:ANDROID_SDK_ROOT
$env:JAVA_HOME = (Get-ChildItem (Join-Path $taskBuild 'jdk') -Directory | Select-Object -First 1).FullName
$taskToolchain = Join-Path $env:ANDROID_SDK_ROOT 'ndk\25.2.9519653\toolchains\llvm\prebuilt\windows-x86_64\bin'
$taskDevkit = Join-Path $taskBuild 'tools\w64devkit\bin'
$env:PATH = "$taskDevkit;$taskToolchain;$env:CARGO_HOME\bin;$env:JAVA_HOME\bin;$env:PATH"
# Limit native compiler memory use on a desktop doing other work.
$env:CARGO_BUILD_JOBS = '4'
$env:CARGO_TARGET_DIR = Join-Path $taskBuild 'mobile-target'
function Invoke-Checked([string]$Command, [string[]]$Arguments) {
    & $Command @Arguments
    if ($LASTEXITCODE -ne 0) { throw "$Command failed with exit code $LASTEXITCODE" }
}
Copy-Item -Path (Join-Path $PSScriptRoot 'java\*') -Destination (Join-Path $taskAndroid 'love\src\main\java') -Recurse -Force
$taskGradleFile = Join-Path $taskAndroid 'love\build.gradle'
$taskGradleText = [IO.File]::ReadAllText($taskGradleFile).Replace('Runtime.runtime.availableProcessors()', '4')
[IO.File]::WriteAllText($taskGradleFile, $taskGradleText)
$taskManifestFile = Join-Path $taskAndroid 'app\src\main\AndroidManifest.xml'
[xml]$taskManifest = [IO.File]::ReadAllText($taskManifestFile)
$taskLauncher = @($taskManifest.manifest.application.activity) | Where-Object { $_.GetAttribute('name','http://schemas.android.com/apk/res/android') -eq 'org.love2d.android.LauncherActivity' }
if ($taskLauncher) { $taskLauncher.RemoveAttribute('label','http://schemas.android.com/apk/res/android'); $taskManifest.Save($taskManifestFile) }
$taskSources = @{'0.9.0' = (Join-Path $taskRoot 'upstream-lovely-090'); '0.10.0' = (Join-Path $taskRoot 'android-lovely')}
foreach ($taskVersion in $Versions) {
    $taskSource = $taskSources[$taskVersion].Replace('\','/')
    $taskBridge = Join-Path $taskBuild "mobile-$taskVersion"
    New-Item -ItemType Directory -Force (Join-Path $taskBridge 'src') | Out-Null
    Copy-Item -LiteralPath (Join-Path $PSScriptRoot 'native\mobile_lib.rs') -Destination (Join-Path $taskBridge 'src\lib.rs') -Force
    $taskCargo = @"
[package]
name = "lmm-lovely-mobile"
version = "$taskVersion"
edition = "2021"
[workspace]
[lib]
name = "lovely"
crate-type = ["cdylib"]
[dependencies]
lovely-core = { path = "$taskSource/crates/lovely-core" }
once_cell = "1.19.0"
"@
    [IO.File]::WriteAllText((Join-Path $taskBridge 'Cargo.toml'), $taskCargo)
    foreach ($taskAbi in $Abis) {
        if ($taskAbi -eq 'arm64-v8a') { $taskTarget = 'aarch64-linux-android'; $taskCompiler = 'aarch64-linux-android21-clang.cmd' }
        else { $taskTarget = 'armv7-linux-androideabi'; $taskCompiler = 'armv7a-linux-androideabi21-clang.cmd' }
        [Environment]::SetEnvironmentVariable(('CARGO_TARGET_' + $taskTarget.Replace('-','_').ToUpperInvariant() + '_LINKER'), (Join-Path $taskToolchain $taskCompiler), 'Process')
        [Environment]::SetEnvironmentVariable(('CC_' + $taskTarget.Replace('-','_')), (Join-Path $taskToolchain $taskCompiler), 'Process')
        [Environment]::SetEnvironmentVariable(('AR_' + $taskTarget.Replace('-','_')), (Join-Path $taskToolchain 'llvm-ar.exe'), 'Process')
        Invoke-Checked (Join-Path $env:CARGO_HOME 'bin\cargo.exe') @('build','--release','--manifest-path',(Join-Path $taskBridge 'Cargo.toml'),'--target',$taskTarget)
        $taskDestination = Join-Path $taskAndroid "app\src\embed\assets\lmm\lovely\$taskVersion\$taskAbi"
        New-Item -ItemType Directory -Force $taskDestination | Out-Null
        Copy-Item -LiteralPath (Join-Path $env:CARGO_TARGET_DIR "$taskTarget\release\liblovely.so") -Destination (Join-Path $taskDestination 'liblovely.so') -Force
    }
}
$taskRuntimeJson = @{mobile_api=1; versions=@($Versions)} | ConvertTo-Json -Compress
[IO.File]::WriteAllText((Join-Path $taskAndroid 'app\src\embed\assets\lmm\runtime.json'),$taskRuntimeJson)
$taskLuaJit = Join-Path $taskAndroid 'love\src\jni\LuaJIT-2.1'
# A cmd wrapper lets native GNU make invoke NDK's target clang reliably on Windows.
foreach ($taskAbi in $Abis) {
    if ($taskAbi -eq 'arm64-v8a') { $taskCompiler='aarch64-linux-android21-clang.cmd'; $taskHostCompiler=Join-Path $taskDevkit 'gcc.exe' }
    else { $taskCompiler='armv7a-linux-androideabi21-clang.cmd'; $taskHostCompiler=Join-Path $taskBuild 'tools-x86\w64devkit\bin\gcc.exe' }
    $taskMake = Join-Path $taskDevkit 'make.exe'
    Invoke-Checked $taskMake @('-C',$taskLuaJit,'clean')
    Invoke-Checked $taskMake @('-C',$taskLuaJit,'-j4',"HOST_CC=$($taskHostCompiler.Replace('\','/'))",'HOST_CFLAGS=-D_CRT_SECURE_NO_WARNINGS',
        "STATIC_CC=$taskCompiler -fPIC", "DYNAMIC_CC=$taskCompiler -fPIC", 'TARGET_AR=llvm-ar rcus','TARGET_SYS=Linux',
        "TARGET_LD=$taskCompiler",'TARGET_LDFLAGS=-fuse-ld=lld','TARGET_LIBS=-ldl','TARGET_STRIP=llvm-strip','BUILDMODE=static',
        'GIT_RELVER=type ..\.relver > luajit_relver.txt','amalg')
    Copy-Item -LiteralPath (Join-Path $taskLuaJit 'src\libluajit.a') -Destination (Join-Path $taskLuaJit "android\$taskAbi\libluajit.a") -Force
    Copy-Item -LiteralPath (Join-Path $taskLuaJit 'src\luajit.h') -Destination (Join-Path $taskLuaJit "android\$taskAbi\luajit.h") -Force
}
$taskLicenses = Join-Path $taskAndroid 'app\src\embed\assets\lmm\licenses'
New-Item -ItemType Directory -Force $taskLicenses | Out-Null
foreach ($taskVersion in $Versions) { Copy-Item -LiteralPath (Join-Path $taskSources[$taskVersion] 'LICENSE.md') -Destination (Join-Path $taskLicenses "Lovely-$taskVersion.txt") -Force }
Copy-Item -LiteralPath (Join-Path $taskLuaJit 'COPYRIGHT') -Destination (Join-Path $taskLicenses 'LuaJIT.txt') -Force
Copy-Item -LiteralPath (Join-Path $taskAndroid 'love\src\jni\love\license.txt') -Destination (Join-Path $taskLicenses 'LOVE.txt') -Force
Push-Location $taskAndroid
try { Invoke-Checked '.\gradlew.bat' @('--no-daemon','assembleEmbedNoRecordRelease') }
finally { Pop-Location }
New-Item -ItemType Directory -Force (Join-Path $taskRoot 'dist') | Out-Null
Copy-Item -LiteralPath (Join-Path $taskAndroid 'app\build\outputs\apk\embedNoRecord\release\app-embed-noRecord-release.apk') -Destination (Join-Path $taskRoot 'dist\base.apk') -Force
Write-Output 'Built dist/base.apk'
