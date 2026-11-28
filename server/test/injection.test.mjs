// SQL injection checks. 1) A guard that fails if any SQL text is built from anything but a fixed string.
// 2) The whole Worker run against a real SQLite (D1 is SQLite) with hostile input in every field of every endpoint.
import fs from 'node:fs';
import { handle } from '../src/index.js';

let fails = 0; const ok = (n, v) => { console.log((v ? 'PASS ' : 'FAIL ') + n); if (!v) fails++; };

// ---- 1) every prepare() takes one fixed quoted string (or a look-up in the fixed COUNT_SQL table), never a built string
const src = fs.readFileSync(new URL('../src/index.js', import.meta.url), 'utf8');
const bad = []; let n = 0;
for (let i = src.indexOf('prepare('); i !== -1; i = src.indexOf('prepare(', i + 1)) {
  n++;
  let j = i + 'prepare('.length;
  if (src.startsWith('COUNT_SQL[which])', j)) continue;
  if (src[j] !== "'") { bad.push(src.slice(i, i + 60)); continue; }
  for (j++; j < src.length && src[j] !== "'"; j++) if (src[j] === '\\') j++;
  if (src[j + 1] !== ')') bad.push(src.slice(i, i + 60));   // a + or anything else after the literal
}
ok('the guard found the SQL statements (' + n + ')', n >= 10);
ok('every SQL text is a fixed string: nothing is joined into it' + (bad.length ? ' -> ' + bad.join(' | ') : ''), bad.length === 0);
const table = src.match(/const COUNT_SQL = \{([\s\S]*?)\};/);
ok('the fixed COUNT_SQL table has only quoted statements with a ? for the value', !!table && table[1].split('\n').filter(l => l.includes(':')).every(l => /^\s*\w+: '[^'+]*\?[^'+]*',?\s*$/.test(l)));
ok('no template literal is used to make SQL', !/prepare\(`/.test(src) && !/\$\{[^}]*\}[^`]*(SELECT|INSERT|UPDATE|DELETE)/i.test(src));

// ---- 2) the Worker on a real SQLite with hostile input
let sqlite;
try { sqlite = await import('node:sqlite'); } catch (e) { console.log('SKIP real SQLite injection run (node:sqlite is not in this Node)'); console.log(fails ? fails + ' FAILED' : 'ALL PASS'); process.exit(fails ? 1 : 0); }
const real = new sqlite.DatabaseSync(':memory:');
real.exec(fs.readFileSync(new URL('../schema.sql', import.meta.url), 'utf8'));
const d1 = { prepare(sql) {
  const st = real.prepare(sql); let args = [];
  const q = { bind(...a) { args = a; return q; },
    async first() { const r = st.get(...args); return r === undefined ? null : r; },
    async run() { const r = st.run(...args); return { meta: { changes: Number(r.changes) } }; },
    async all() { return { results: st.all(...args) }; } };
  return q;
} };
const env = { DB: d1 };
const schemaBefore = () => JSON.stringify(real.prepare("SELECT type, name, sql FROM sqlite_master ORDER BY name").all());

const post = async (path, obj, raw) => {
  const r = await handle(new Request('https://x.test' + path, { method: 'POST', body: raw !== undefined ? raw : JSON.stringify(obj) }), env);
  let j = null; try { j = await r.json(); } catch (e) { /* not json */ }
  return [r.status, j];
};
const get = async (path, ua) => handle(new Request('https://x.test' + path, { method: 'GET', headers: { 'user-agent': ua || 'Chrome' } }), env);

// legitimate state first: two phones, one code, one redemption, one counted click
const alice = 'alice-device-0000001', bob = 'bob-device-00000001';
let [s, j] = await post('/v1/code', { device: alice });
const aliceCode = j.code;
await post('/v1/code', { device: bob });
await post('/v1/redeem', { device: bob, code: aliceCode });
await get('/i/' + aliceCode);
const schema0 = schemaBefore();
const rows = t => real.prepare('SELECT * FROM ' + t).all();   // test-only
const snapshot = () => JSON.stringify([rows('codes'), rows('redemptions'), rows('clicks')]);
const state0 = snapshot();

const evil = [
  "' OR '1'='1", "' OR 1=1 --", "'; DROP TABLE codes; --", "x'); DELETE FROM redemptions; --", '" OR ""="',
  "1; SELECT * FROM codes", "%' UNION SELECT device,code,created FROM codes --", "\\'; --", "Robert'); DROP TABLE students;--",
  "' || (SELECT code FROM codes LIMIT 1) || '", "\u0000", "💥", "a".repeat(5000), "0x27", "ZZZZZZZZ' OR code LIKE '%", "ZZZZZZZZ\n'; --",
  "alice-device-0000001' OR '1'='1", aliceCode + "'; UPDATE redemptions SET claimed = 1; --",
];
const odd = [null, undefined, 0, 1, true, [], [aliceCode], {}, { code: aliceCode }, -1, 1e999, "", " "];
const endpoints = ['/v1/code', '/v1/redeem', '/v1/status', '/v1/claim', '/v1/active', '/v1/nothing', '/v1/code/../redeem', "/v1/code'--"];
let attempts = 0, errors500 = 0, leaked = 0, accepted = 0;
for (const ep of endpoints) {
  for (const v of [...evil, ...odd]) {
    for (const body of [{ device: v }, { device: v, code: v }, { device: alice, code: v }, { code: v }, v]) {
      const [st, jj] = await post(ep, body); attempts++;
      if (st >= 500) errors500++;
      if (jj && jj.ok && (v !== alice) && ep !== '/v1/nothing' && typeof v === 'string' && !/^[A-Za-z0-9-]{16,64}$/.test(v) && body.device === v) accepted++;
      if (jj && jj.code === aliceCode && !(body.device === alice)) leaked++;
    }
  }
  for (const raw of ['{', '[]', 'null', '"x"', "{'device':'x'}", '{"device":"' + 'a'.repeat(3000) + '"}', '\u0000']) { const [st] = await post(ep, null, raw); attempts++; if (st >= 500) errors500++; }
}
// hostile device ids that DO pass the id format (it allows dashes: "--" is just text) and codes that pass the code format
const dashy = ['a-- OR 1=1 ------', '1-OR-1-1-1-1-1-1-1', 'x--DROP-TABLE-codes--', 'aaaaaaaaaaaaaaaa--'];
for (const d of dashy) { await post('/v1/code', { device: d }); await post('/v1/status', { device: d }); await post('/v1/redeem', { device: d, code: 'ZZZZZZZZ' }); attempts += 3; }
// the link path
for (const p of evil.concat(odd.filter(x => typeof x === 'string'))) { const r = await get('/i/' + encodeURIComponent(String(p))); attempts++; if (r.status >= 500) errors500++; }
for (const p of ["ABCD2345'--", "ABCD2345%27%20OR%201=1", "../../etc/passwd", "ZZZZZZZZ", "A'B"]) { const r = await get('/i/' + p); attempts++; if (r.status >= 500) errors500++; }

ok('hostile requests were sent (' + attempts + ')', attempts > 1000);
ok('none of them made the server fail (no 500)', errors500 === 0);
ok('no hostile device id was accepted as a phone', accepted === 0);
ok("nobody got alice's code by tampering", leaked === 0);
ok('the tables and indexes are exactly as they were (nothing dropped, nothing added)', schemaBefore() === schema0);
const after = { codes: rows('codes'), redemptions: rows('redemptions'), clicks: rows('clicks') };
ok('the legitimate rows are untouched', JSON.stringify(after.codes.slice(0, 2)) === JSON.stringify(JSON.parse(state0)[0]) && JSON.stringify(after.redemptions) === JSON.stringify(JSON.parse(state0)[1]));
ok('alice\'s redemption and click counts did not change', after.redemptions.length === 1 && after.redemptions[0].claimed === -1 && after.clicks.length === 1 && after.clicks[0].n >= 1);
ok('every phone id in the database has the id format', after.codes.every(r => /^[A-Za-z0-9-]{16,64}$/.test(r.device)));
ok('every code in the database has the code format', after.codes.every(r => /^[A-Z2-9]{8}$/.test(r.code)));
ok('only the phones that passed the id format got a code (alice, bob and the dash ids that fit the format)', after.codes.length === 2 + dashy.filter(d => /^[A-Za-z0-9-]{16,64}$/.test(d)).length);
ok('no hostile text was stored anywhere', JSON.stringify(after).indexOf("'") === -1 && JSON.stringify(after).indexOf(';') === -1);
if (process.env.SHOW) console.log(after.codes.map(r => r.device));
console.log(fails ? fails + ' FAILED' : 'ALL PASS'); process.exit(fails ? 1 : 0);
