const fs = require('fs');
const SRC = 'D:/repos/botwmap-src/app';

function readVar(f, v) {
  const t = fs.readFileSync(SRC + '/' + f, 'utf8');
  const prefix = 'var ' + v + ' =';
  const i = t.indexOf(prefix);
  const b = t.slice(i + prefix.length);
  const s = b.search(/[[{]/);
  const o = b[s];
  const c = o === '[' ? ']' : '}';
  let d = 0, q = false, e = false, end = -1;
  for (let j = s; j < b.length; j++) {
    const ch = b[j];
    if (q) {
      if (e) e = false;
      else if (ch === '\\') e = true;
      else if (ch === '"') q = false;
      continue;
    }
    if (ch === '"') q = true;
    else if (ch === o) d++;
    else if (ch === c) { d--; if (d === 0) { end = j; break; } }
  }
  return JSON.parse(b.slice(s, end + 1));
}

const m = readVar('js/materials.js', 'BOTW_MATERIALS');
console.log('materials:', m.materials.length, 'points len:', m.points ? m.points.length : 'NONE', 'count:', m.points ? m.points.length / 3 : 0);

const d = readVar('js/dragon_paths.js', 'DRAGON_PATHS');
console.log('dragon keys:', Object.keys(d), 'items:', d.items.length);
console.log('item0:', JSON.stringify(d.items[0]).slice(0, 500));

const k = readVar('js/korok_paths.js', 'KOROK_PATHS');
console.log('korok paths:', k.items.length, 'item0 keys:', Object.keys(k.items[0]));
console.log('item0:', JSON.stringify(k.items[0]).slice(0, 300));
