/* 原项目存档查找表 → Android assets JSON
 * 输入（botwmap-src/app/data）：
 *   save_flags.js   SAVE_FLAGS {hash: [type,id]}          → save_flags.json  [[h,t,id]]
 *   quest_save.js   QUEST_SAVE {flagName: hash}           → quest_flags.json [[h,key]]（结合 quests.js 的 flag 字段）
 *   enemy_save.js   ENEMY_SAVE {hash2pt: {hash:[px,py]}}  → enemy_points.json [[h,px,py]]
 * hash 为 u32（< 2^53），JSON number 精度安全。
 */
'use strict';
const fs = require('fs');
const path = require('path');

const SRC = 'D:/repos/botwmap-src/app/data';
const OUT = 'D:/repos/BOTWMap/app/src/main/assets/data';

function loadJs(file, varName) {
  let s = fs.readFileSync(path.join(SRC, file), 'utf8');
  // 剥注释头与 var 声明：取第一个 '=' 后到最后
  const i = s.indexOf(varName);
  if (i < 0) throw new Error(`${varName} not found in ${file}`);
  const eq = s.indexOf('=', i);
  let body = s.slice(eq + 1).trim();
  if (body.endsWith(';')) body = body.slice(0, -1);
  return JSON.parse(body);
}

// 1) save_flags（多值条目 v=[[t,id],...] 展开为多行，单值 v=[t,id,...]）
const sf = loadJs('save_flags.js', 'SAVE_FLAGS');
const flagsArr = [];
for (const [h, v] of Object.entries(sf)) {
  const refs = Array.isArray(v[0]) ? v : [v];
  for (const ref of refs) {
    if (typeof ref[0] === 'string' && ref[1]) flagsArr.push([Number(h), ref[0], ref[1]]);
  }
}
fs.writeFileSync(path.join(OUT, 'save_flags.json'), JSON.stringify(flagsArr));
console.log('save_flags.json:', flagsArr.length);

// 2) quest_save（flagName→hash）+ quests.js（key→flag）→ hash→key
const qs = loadJs('quest_save.js', 'QUEST_SAVE');
const questsRaw = loadJs('quests.js', 'BOTW_QUESTS');
const questList = Array.isArray(questsRaw) ? questsRaw : (questsRaw.quests || []);
const flagToKey = {};
for (const q of questList) if (q.flag && q.key) flagToKey[q.flag] = q.key;
const questArr = [];
let miss = 0;
for (const [flagName, h] of Object.entries(qs)) {
  const key = flagToKey[flagName];
  if (key) questArr.push([Number(h), key]);
  else miss++;
}
fs.writeFileSync(path.join(OUT, 'quest_flags.json'), JSON.stringify(questArr));
console.log('quest_flags.json:', questArr.length, 'unmatched flags:', miss);

// 3) enemy_save hash2pt
const es = loadJs('enemy_save.js', 'ENEMY_SAVE');
const h2p = es.hash2pt || es;
const enemyArr = Object.entries(h2p)
  .filter(([h, v]) => /^\d+$/.test(h) && Array.isArray(v))
  .map(([h, v]) => [Number(h), v[0], v[1]]);
fs.writeFileSync(path.join(OUT, 'enemy_points.json'), JSON.stringify(enemyArr));
console.log('enemy_points.json:', enemyArr.length);
