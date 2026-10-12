import fs from 'node:fs';
import path from 'node:path';
import crypto from 'node:crypto';
const base = process.argv[2];
const dist = path.resolve(process.argv[3] || 'dist');
if (!base) { console.log('No LOVELY_DOWNLOAD_BASE set; bundled versions work offline. Skipped online catalog.'); process.exit(0); }
if (new URL(base).protocol !== 'https:') throw new Error('LOVELY_DOWNLOAD_BASE must be HTTPS');
const catalog = { mobile_api: 1, releases: [] };
for (const version of fs.readdirSync(path.join(dist, 'lovely')).sort()) {
    const libraries = {};
    for (const abi of ['arm64-v8a', 'armeabi-v7a']) {
        const relative = `lovely/${version}/${abi}/liblovely.so`;
        const bytes = fs.readFileSync(path.join(dist, relative));
        libraries[abi] = { url: `${base.replace(/\/$/, '')}/${relative}`, sha256: crypto.createHash('sha256').update(bytes).digest('hex') };
    }
    catalog.releases.push({ version, libraries });
}
fs.writeFileSync(path.join(dist, 'lovely-catalog.json'), JSON.stringify(catalog, null, 2));
