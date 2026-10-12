import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const here = path.dirname(fileURLToPath(import.meta.url));
const root = path.resolve(process.argv[2] || '.build/android');
const patch = (file, transform) => {
    const target = path.join(root, file);
    const source = fs.readFileSync(target, 'utf8').replaceAll('\r\n', '\n');
    fs.writeFileSync(target, transform(source));
};
function replace(source, from, to, count = 1) {
    const matches = source.split(from).length - 1;
    if (matches !== count) throw new Error(`Expected ${count} matches for ${from.slice(0, 80)}, found ${matches}. Use a fresh pinned checkout.`);
    return source.replaceAll(from, to);
}

fs.cpSync(path.join(here, 'java'), path.join(root, 'love/src/main/java'), { recursive: true });
fs.appendFileSync(path.join(root, 'app/proguard-rules.pro'), '\n-keepclassmembers class org.love2d.android.LauncherActivity$LocalBridge { @android.webkit.JavascriptInterface <methods>; }\n');
const luajit = 'love/src/jni/LuaJIT-2.1/src/';
fs.copyFileSync(path.join(here, 'native/lovely.h'), path.join(root, luajit, 'lovely.h'));
fs.copyFileSync(path.join(here, 'native/lovely_bridge.c'), path.join(root, luajit, 'lmm_lovely_bridge.c'));
patch(luajit + 'lauxlib.h', source => replace(source,
    'LUALIB_API int (luaL_loadbufferx)',
    'LUALIB_API int (lovely_loadbufferx) (lua_State *L, const char *buff, size_t sz, const char *name, const char *mode);\nLUALIB_API int (luaL_loadbufferx)'));
patch(luajit + 'lib_aux.c', source => {
    source = replace(source, '#include "lj_vmevent.h"', '#include "lj_vmevent.h"\n#include "lmm_lovely_bridge.c"');
    return replace(source, 'LUALIB_API lua_State *luaL_newstate(void)\n{',
        'LUALIB_API lua_State *luaL_newstate(void)\n{\n  struct LuaLib lua = {lua_call, lua_pcall, lua_getfield, lua_setfield, lua_gettop, lua_settop, lua_pushvalue, lua_pushcclosure, lua_tolstring, lua_type, lua_pushstring, lua_pushnumber, lua_pushboolean, lua_settable, lua_createtable, lua_error, luaL_register, luaL_checklstring};\n  lovely_init(lovely_loadbufferx, lua);', 2);
});
patch(luajit + 'lj_load.c', source => {
    source = replace(source, '#include "lj_parse.h"', '#include "lj_parse.h"\n#include "lovely.h"');
    source = replace(source, 'LUALIB_API int luaL_loadbufferx(lua_State *L', 'LUALIB_API int lovely_loadbufferx(lua_State *L');
    return replace(source, 'LUALIB_API int luaL_loadbuffer(lua_State *L',
        'LUALIB_API int luaL_loadbufferx(lua_State *L, const char *buf, size_t size, const char *name, const char *mode)\n{\n  return lovely_apply_patches(L, buf, size, name, mode);\n}\n\nLUALIB_API int luaL_loadbuffer(lua_State *L');
});
patch('app/src/main/AndroidManifest.xml', source => {
    source = replace(source, 'android:name="org.love2d.android.GameActivity"',
        'android:name="org.love2d.android.ManagedGameActivity"\n        android:process=":game"');
    source = replace(source, 'android:launchMode="singleInstance"', 'android:launchMode="standard"');
    source = replace(source, 'android:exported="true"\n        android:configChanges', 'android:exported="false"\n        android:configChanges');
    source = replace(source,
        '      <intent-filter>\n        <action android:name="android.intent.action.MAIN" />\n        <category android:name="android.intent.category.LAUNCHER" />\n        <category android:name="tv.ouya.intent.category.GAME" />\n      </intent-filter>\n', '');
    return replace(source, '    <provider', `    <activity android:name="org.love2d.android.LauncherActivity"
        android:exported="true"
        android:configChanges="orientation|screenSize|keyboardHidden"
        android:theme="@android:style/Theme.Material.NoActionBar">
      <intent-filter>
        <action android:name="android.intent.action.MAIN" />
        <category android:name="android.intent.category.LAUNCHER" />
      </intent-filter>
    </activity>
    <provider`);
});
patch('love/build.gradle', source => replace(source, "abiFilters += 'x86_64'", "// Mobile runtime bundles target ARM only."));
patch('app/build.gradle', source => replace(source, 'minSdk 21', 'minSdk 24'));
patch('gradle.properties', source => replace(source, 'app.version_code=32', 'app.version_code=35'));
// Include a capability marker: the web maker must never advertise updating on a legacy base.
const assets = path.join(root, 'app/src/embed/assets/lmm');
fs.mkdirSync(assets, { recursive: true });
fs.cpSync(path.join(here, 'ui'), path.join(assets, 'ui'), { recursive: true });
fs.writeFileSync(path.join(assets, 'runtime.json'), JSON.stringify({ mobile_api: 1, mod_manager: 2, launcher_ui: 2, versions: ['0.9.0', '0.10.0'] }));
console.log('Applied mobile launcher, versioned Lovely bridge, and runtime capability marker.');
