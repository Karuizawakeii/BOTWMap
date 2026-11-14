/**
 * 数据转换脚本：BOTWmap (yalincc) JS 数据 → Android assets JSON
 * 用法：node tools/convert-data.js <原仓库app目录> <输出assets目录>
 */
const fs = require('fs');
const path = require('path');

const SRC = process.argv[2] || 'D:/repos/botwmap-src/app';
const OUT = process.argv[3] || 'D:/repos/BOTWMap/app/src/main/assets';

function readJsVar(file, varName) {
  const text = fs.readFileSync(path.join(SRC, file), 'utf8');
  // 支持 "var X =" 与 "window.X=" 两种声明
  const candidates = [`var ${varName} =`, `window.${varName}=`, `window.${varName} =`];
  let idx = -1, prefix = '';
  for (const p of candidates) {
    idx = text.indexOf(p);
    if (idx >= 0) { prefix = p; break; }
  }
  if (idx < 0) throw new Error(`${file}: 未找到 ${varName}`);
  const body = text.slice(idx + prefix.length);
  // 从第一个 [ 或 { 起做括号配平，截出完整 JSON（跳过尾部附加 JS 语句）
  const start = body.search(/[[{]/);
  if (start < 0) throw new Error(`${file}: 无 JSON 主体`);
  const open = body[start];
  const close = open === '[' ? ']' : '}';
  let depth = 0, inStr = false, esc = false, end = -1;
  for (let i = start; i < body.length; i++) {
    const c = body[i];
    if (inStr) {
      if (esc) esc = false;
      else if (c === '\\') esc = true;
      else if (c === '"') inStr = false;
      continue;
    }
    if (c === '"') inStr = true;
    else if (c === open || (open === '[' && c === '[') || (open === '{' && c === '{')) depth++;
    else if (c === close) {
      depth--;
      if (depth === 0) { end = i; break; }
    }
  }
  if (end < 0) throw new Error(`${file}: 括号不配平`);
  return JSON.parse(body.slice(start, end + 1));
}

function ensureDir(p) {
  fs.mkdirSync(p, { recursive: true });
}

function writeJson(rel, obj) {
  const out = path.join(OUT, rel);
  ensureDir(path.dirname(out));
  fs.writeFileSync(out, JSON.stringify(obj));
  console.log(`  ${rel}  ${(fs.statSync(out).size / 1024).toFixed(0)} KB`);
}

/** 图层分组：27 类 → LayerGroup */
const GROUP_MAP = {
  tower: 'EXPLORE', shrine: 'EXPLORE', lab: 'EXPLORE', seed: 'EXPLORE',
  memory: 'EXPLORE', beast: 'EXPLORE',
  mainquest: 'QUEST', shrinequest: 'QUEST', sidequest: 'QUEST', objective: 'QUEST',
  stable: 'FACILITY', village: 'FACILITY', inn: 'FACILITY', store: 'FACILITY',
  armor: 'FACILITY', dye: 'FACILITY', jewelry: 'FACILITY', settlement: 'FACILITY',
  fountain: 'FACILITY', statue: 'FACILITY', pot: 'FACILITY',
  talus: 'ENEMY', hinox: 'ENEMY', lynel: 'ENEMY', molduga: 'ENEMY', guardian: 'ENEMY',
  treasure: 'LOOT',
};

/** 类目 → 游戏图标（提取自 map.js CAT_CFG，assets/icons/ 实际文件名） */
const CAT_ICON = {
  tower: 'icons_01.png', shrine: 'icons_03.png', beast: 'icons_31.png',
  lab: 'icons_05.png', seed: 'icons_04.png', memory: 'icons_28.png',
  treasure: 'icons_14.png', stable: 'icons_10.png', village: 'icons_08.png',
  inn: 'icons_07.png', store: 'icons_06.png', armor: 'icons_09.png',
  dye: 'icons_11.png', jewelry: 'icons_33.png', settlement: 'icons_34.png',
  fountain: 'icons_19.png', statue: 'icons_22.png', pot: 'icons_16.png',
  raft: 'icons_26.png', talus: 'icons_20.png', hinox: 'icons_12.png',
  lynel: 'icons_24.png', molduga: 'molduga.png', guardian: 'icons_35.png',
  bokoblin: 'icons_36.png', moblin: 'icons_37.png', lizalfos: 'icons_38.png',
  keese: 'icons_39.png', chuchu: 'icons_40.png', octorok: 'icons_41.png',
  wizzrobe: 'icons_42.png', yiga: 'icons_43.png', pebblit: 'icons_45.png',
  mainquest: 'icons_31.png', shrinequest: 'icons_30.png',
  sidequest: 'icons_05.png', objective: 'icons_32.png',
};

console.log('== 转换主点位数据 (data.js) ==');
const botw = readJsVar('js/data.js', 'BOTW_DATA');
const markers = botw.markers.map((m) => ({
  id: m.id,
  name: m.name || '',
  en: m.en || '',
  cat: m.cat,
  px: m.px[0],
  py: m.px[1],
  region: m.region || '',
  hint: m.extra && m.extra.hint ? m.extra.hint : '',
  // 类目图标优先（CAT_CFG 映射到实际文件），语义名兜底
  icon: CAT_ICON[m.cat] || (m.extra && m.extra.icon ? m.extra.icon : ''),
}));
const categories = botw.categories.map((c) => ({
  key: c.key,
  cn: c.cn,
  en: c.en,
  group: GROUP_MAP[c.key] || 'FACILITY',
  icon: CAT_ICON[c.key] || '',
}));
writeJson('data/markers.json', {
  mapW: botw.meta.mapW,
  mapH: botw.meta.mapH,
  version: botw.meta.version,
  categories,
  markers,
});
console.log(`  点位 ${markers.length} 个 / 类目 ${categories.length} 个`);

console.log('== 转换瓦片索引 (tiles_idx.js) ==');
const pack = readJsVar('assets/tiles/tiles_idx.js', 'BOTW_TILES_PACK');
writeJson('data/tiles_idx.json', { packs: pack.packs, idx: pack.idx });
console.log(`  packs ${pack.packs.length} 个 / 索引 ${Object.keys(pack.idx).length} 条`);

console.log('== 转换克洛格 (koroks.js) ==');
const koroks = readJsVar('js/koroks.js', 'BOTW_KOROKS');
writeJson('data/koroks.json', {
  total: koroks.meta.total,
  items: koroks.items.map((k) => ({
    id: k.id, no: k.no, hintCn: k.hint_cn || '', hintEn: k.hint_en || '',
    img: k.img || '',
  })),
});

console.log('== 转换材料 (materials.js) ==');
const mats = readJsVar('js/materials.js', 'BOTW_MATERIALS');
writeJson('data/materials.json', {
  total: mats.meta.count,
  materials: mats.materials.map((m) => ({
    id: m.id, cat: m.cat, cn: m.cn, en: m.en || '',
    entry: m.entry || '', count: m.count || 0,
  })),
  // 扁平刷点 [id,px,py, id,px,py, ...]（13524 点），Kotlin 端按 id 分组
  pointsFlat: mats.points || [],
});
console.log(`  刷点 ${(mats.points || []).length / 3} 个`);

console.log('== 转换轨迹线 (dragon_paths / korok_paths) ==');
const dragons = readJsVar('js/dragon_paths.js', 'DRAGON_PATHS');
writeJson('data/dragon_paths.json', {
  dragons: dragons.items.map((d) => ({
    name: d.name, color: d.color,
    start: d.start || [],
    segments: d.segments || [],
  })),
});
const korokPaths = readJsVar('js/korok_paths.js', 'KOROK_PATHS');
writeJson('data/korok_paths.json', {
  total: korokPaths.meta.total,
  paths: korokPaths.items.map((p) => ({
    id: p.id, no: p.no, type: p.type || '',
    locCn: p.loc_cn || '', path: p.path || [],
  })),
});
console.log(`  龙 ${dragons.items.length} 条 / 克洛格轨迹 ${korokPaths.items.length} 条`);

console.log('== 转换装备 (armors.js / weapons.js) ==');
const armors = readJsVar('data/armors.js', 'BOTW_ARMORS');
writeJson('data/armors.json', {
  total: armors.meta.count,
  items: Object.values(armors.items).map((a) => ({
    id: a.content, name: a.name, en: a.en || '',
    part: a.part || '', def: a.def ?? 0, set: a.set || '',
  })),
});
const weapons = readJsVar('data/weapons.js', 'BOTW_WEAPONS');
writeJson('data/weapons.json', {
  total: weapons.meta.count,
  items: Object.values(weapons.items).map((w) => ({
    id: w.content, name: w.name, en: w.en || '',
    type: w.type || '', atk: w.atk ?? 0,
  })),
});

console.log('== 转换任务 (quests.js) ==');
const quests = readJsVar('data/quests.js', 'BOTW_QUESTS');
writeJson('data/quests.json', {
  total: quests.meta.total,
  counts: quests.meta.counts,
  quests: quests.quests.map((q) => ({
    key: q.key, no: q.no, groupTotal: q.groupTotal, cat: q.cat,
    name: q.name, en: q.en || '', region: q.region || '',
    npc: q.npc || '', desc: q.desc || '',
    pts: (q.pts || []).map((p) => [p[0], p[1]]),
  })),
});
console.log(`  任务 ${quests.quests.length} 条`);

console.log('== 转换地名 (regions.js) ==');
const regions = readJsVar('js/regions.js', 'BOTW_REGIONS');
writeJson('data/regions.json', regions);

/** 递归拷贝目录 */
function copyDir(from, to) {
  ensureDir(to);
  let n = 0;
  for (const name of fs.readdirSync(from)) {
    const s = path.join(from, name);
    const d = path.join(to, name);
    if (fs.statSync(s).isDirectory()) copyDir(s, d);
    else { fs.copyFileSync(s, d); n++; }
  }
  return n;
}

console.log('== 拷贝瓦片 ==');
const n1 = copyDir(path.join(SRC, 'assets/tiles'), path.join(OUT, 'tiles'));
console.log(`  ${n1} 个文件`);

console.log('== 拷贝图标 ==');
const n2 = copyDir(path.join(SRC, 'assets/icons'), path.join(OUT, 'icons'));
const n3 = copyDir(path.join(SRC, 'assets/materials'), path.join(OUT, 'materials'));
console.log(`  icons ${n2} 个 / materials ${n3} 个`);

console.log('== 拷贝克洛格截图 ==');
const n4 = copyDir(path.join(SRC, 'assets/korok'), path.join(OUT, 'korok'));
console.log(`  korok ${n4} 张`);

console.log('完成。');
