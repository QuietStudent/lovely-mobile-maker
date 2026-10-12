// Bundle the existing maker into an offline, double-clickable HTML file.
// This small bundler handles only this repository's known ESM import/export syntax.
import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');
const web = path.join(root, 'dist/web');
const files = fs.readdirSync(path.join(root, 'js')).filter(name => name.endsWith('.js'));
const modules = {};
for (const name of files) modules[name] = fs.readFileSync(path.join(root, 'js', name), 'utf8');
modules['pkg/mbf_bindgen.js'] = fs.readFileSync(path.join(web, 'js/pkg/mbf_bindgen.js'), 'utf8');
const wasm = fs.readFileSync(path.join(web, 'js/pkg/mbf_bindgen_bg.wasm')).toString('base64');
const imageUrl = name => `data:image/png;base64,${fs.readFileSync(path.join(root, 'img', name)).toString('base64')}`;
const images = Object.fromEntries(fs.readdirSync(path.join(root, 'img')).filter(name => name.endsWith('.png')).map(name => [name, imageUrl(name)]));
const sources = [];
for (let [name, code] of Object.entries(modules)) {
    const namedExports = [];
    code = code.replace(/^import\s+(.+?)\s+from\s+['"](.+?)['"];?\s*$/gm, (_, binding, specifier) => {
        const target = path.posix.normalize(path.posix.join(path.posix.dirname(name), specifier));
        if (!modules[target]) throw new Error(`Unknown module ${target}`);
        if (binding.startsWith('* as ')) return `const ${binding.slice(5)} = __require(${JSON.stringify(target)});`;
        const fields = [];
        const braces = binding.match(/\{([^}]+)\}/);
        if (braces) fields.push(braces[1].trim());
        const first = binding.split(',')[0].trim();
        if (!first.startsWith('{')) fields.unshift(`default: ${first}`);
        return `const { ${fields.join(', ')} } = __require(${JSON.stringify(target)});`;
    });
    code = code.replace(/^export\s+(function|class)\s+(\w+)/gm, (_, type, identifier) => { namedExports.push(identifier); return `${type} ${identifier}`; });
    code = code.replace(/^export\s*\{([\s\S]*?)\};?\s*$/gm, (_, names) => `Object.assign(exports, { ${names} });`);
    code = code.replace(/^export default (.+);?\s*$/gm, (_, value) => `exports.default = ${value.replace(/;$/, '')};`);
    code = code.replaceAll('import.meta.url', 'location.href');
    if (name === 'pkg/mbf_bindgen.js') {
        const initializer = "module_or_path = new URL('mbf_bindgen_bg.wasm', location.href);";
        if (!code.includes(initializer)) throw new Error('WASM wrapper changed; update the standalone initializer.');
        code = code.replace(initializer, `module_or_path = Uint8Array.from(atob('${wasm}'), c => c.charCodeAt(0));`);
    }
    for (const [image, url] of Object.entries(images)) code = code.replaceAll(`/img/${image}`, url);
    if (name === 'icon.js') {
        // The icon list changes with the platform; preserve this ESM live binding.
        code += '\nObject.defineProperty(exports, "icons", { get: () => icons });\n';
    }
    if (namedExports.length) code += `\nObject.assign(exports, { ${namedExports.join(', ')} });\n`;
    if (/^\s*(import|export)\s/m.test(code)) throw new Error(`Unhandled ESM syntax in ${name}`);
    sources.push(`${JSON.stringify(name)}: function(__require, exports) {\n${code}\n}`);
}
const cert = fs.readFileSync(path.join(root, 'debug-cert.pem')).toString('base64');
const optionalBase = process.argv[2] ? fs.readFileSync(path.resolve(process.argv[2])).toString('base64') : null;
const setup = `
const originalFetch = window.fetch.bind(window);
window.fetch = async function(resource, options) {
 const url = typeof resource === 'string' ? resource : resource.url;
 if (url === '/debug-cert.pem') return new Response(Uint8Array.from(atob('${cert}'), c=>c.charCodeAt(0)));
 if (url === '/base.apk') {
  ${optionalBase ? `return new Response(Uint8Array.from(atob('${optionalBase}'), c=>c.charCodeAt(0)));` : `throw new Error('Choose the Android base.apk file in Step 2, then select Android again.');`}
 }
 if (url === '/base.ipa') throw new Error('This standalone test builds Android APKs.');
 return originalFetch(resource, options);
};
const factories = {${sources.join(',\n')}};
const cache = Object.create(null);
function __require(name) {
 if (cache[name]) return cache[name];
 const exports = cache[name] = {};
 factories[name](__require, exports);
 return exports;
}
__require('main.js');
`;
let css = fs.readFileSync(path.join(root, 'css/main.css'), 'utf8');
for (const [image, url] of Object.entries(images)) css = css.replaceAll(`../img/${image}`, url);
let html = fs.readFileSync(path.join(root, 'index.html'), 'utf8');
html = html.replace(/\s*<link[^>]+href='css\/main.css'>/, `\n<style>${css}</style>`)
    .replace(/\s*<script[^>]+src='js\/main.js'[^>]*><\/script>/, '')
    .replace('<h1>Lovely Mobile Maker</h1>', `<h1>Lovely Mobile Maker — Local Test</h1><p class="step">This file runs locally and does not upload your game files. ${optionalBase ? 'The Android base is included.' : 'First choose your Balatro.exe or .love file, then supply the base.apk from the Android build workflow in Step 2.'} Lovely 0.9.0 is the default when using the updater base.</p>`)
    .replace('<option value="ios">iOS</option>', '<option value="ios" disabled>iOS (not included in this test)</option>')
    .replace('</body>', `<script>${setup.replaceAll('</script', '<\\/script')}</script>\n</body>`);
html = html.replaceAll('href="/faq"', 'href="https://github.com/WilsontheWolf/lovely-mobile-maker"');
fs.mkdirSync(path.join(root, 'dist'), { recursive: true });
fs.mkdirSync(path.join(root, '.build'), { recursive: true });
fs.writeFileSync(path.join(root, 'dist/Lovely-Mobile-Maker.html'), html);
fs.writeFileSync(path.join(root, '.build/standalone-bundle.js'), setup);
console.log(`Created dist/Lovely-Mobile-Maker.html (${Buffer.byteLength(html)} bytes).`);
