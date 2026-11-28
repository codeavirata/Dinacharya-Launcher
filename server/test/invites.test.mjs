import { handle, playUrl, isBot, FRIEND_DAYS, INVITER_DAYS, INVITER_DAYS_LATER, TIER_ONE_COUNT, MAX_REWARDED_INVITES } from '../src/index.js';

// A pretend D1 that understands exactly the statements the Worker uses.
function fakeDb() {
  let db;
  const codes = new Map(), reds = new Map(), clicks = new Map();
  db = { clicks, down: false, prepare(sql) {
    let args = [];
    const q = {
      bind(...a) { args = a; return q; },
      async first() {
        if (sql.startsWith('SELECT code FROM codes')) { const r = codes.get(args[0]); return r ? { code: r.code } : null; }
        if (sql.startsWith('SELECT device FROM codes')) { for (const [d, r] of codes) if (r.code === args[0]) return { device: d }; return null; }
        if (sql.startsWith('SELECT 1 FROM redemptions')) return reds.has(args[0]) ? { 1: 1 } : null;
        if (sql.startsWith('SELECT created, claimed FROM redemptions')) { const r = reds.get(args[0]); return r ? { created: r.created, claimed: r.claimed } : null; }
        if (sql.includes('FROM redemptions WHERE inviter = ?')) {
          const m = sql.match(/AND rewarded = 1 AND claimed = (-?\d)/);
          return { n: [...reds.values()].filter(r => r.inviter === args[0] && (!m || (r.rewarded && r.claimed === Number(m[1])))).length };
        }
        throw new Error('first: ' + sql);
      },
      async run() {
        if (db.down) throw new Error('database is down');
        if (sql.startsWith('INSERT INTO clicks')) {
          const [code, now, again] = args;
          let has = false; for (const r of codes.values()) if (r.code === again) has = true;
          if (!has) return {};
          const c = clicks.get(code); clicks.set(code, c ? { n: c.n + 1, last: now } : { n: 1, last: now }); return {};
        }
        if (sql.startsWith('INSERT INTO codes')) {
          for (const r of codes.values()) if (r.code === args[1]) throw new Error('unique');
          if (codes.has(args[0])) throw new Error('pk'); codes.set(args[0], { code: args[1] }); return {};
        }
        if (sql.startsWith('INSERT INTO redemptions')) { if (reds.has(args[0])) throw new Error('pk'); reds.set(args[0], { inviter: args[1], rewarded: args[2], claimed: -1, created: args[3] }); return {}; }
        if (sql.startsWith('UPDATE redemptions SET claimed = 0 WHERE friend')) { const r = reds.get(args[0]); if (r && r.claimed === -1) { r.claimed = 0; return { meta: { changes: 1 } }; } return { meta: { changes: 0 } }; }
        if (sql.startsWith('UPDATE redemptions SET claimed = 1')) { let n = 0; for (const r of reds.values()) if (r.inviter === args[0] && r.rewarded && r.claimed === 0) { r.claimed = 1; n++; } return { meta: { changes: n } }; }
        throw new Error('run: ' + sql);
      },
    };
    return q;
  } };
  return db;
}
let fails = 0; const ok = (n, v) => { console.log((v ? 'PASS ' : 'FAIL ') + n); if (!v) fails++; };
let clock = 1_800_000_000_000;
const HOUR = 3600 * 1000;
const env = { DB: fakeDb(), NOW: () => clock };
const post = async (p, o) => { const r = await handle(new Request('https://x.test' + p, { method: 'POST', body: JSON.stringify(o) }), env); return [r.status, await r.json()]; };
const dev = n => 'device-' + String(n).padStart(12, '0');

let [s, j] = await post('/v1/code', { device: dev(1) });
ok('a phone gets an 8-letter code', s === 200 && /^[A-Z2-9]{8}$/.test(j.code));
const code = j.code;
[s, j] = await post('/v1/code', { device: dev(1) });
ok('the same phone gets the same code', j.code === code);
[s, j] = await post('/v1/code', { device: 'bad' });
ok('a bad device id is refused', s === 400);
[s, j] = await post('/v1/redeem', { device: dev(1), code });
ok('your own code does not work', s === 400 && !j.ok);
[s, j] = await post('/v1/redeem', { device: dev(2), code: 'ZZZZZZZZ' });
ok('an unknown code is refused', s === 400);
[s, j] = await post('/v1/redeem', { device: dev(2), code: ' ' + code.toLowerCase().slice(0, 4) + '-' + code.slice(4).toLowerCase() + ' ' });
ok('a friend redeems (any case, dash, spaces)', s === 200 && j.ok && j.days === FRIEND_DAYS);
[s, j] = await post('/v1/redeem', { device: dev(2), code });
ok('one invite code per phone', s === 400);
[s, j] = await post('/v1/status', { device: dev(1) });
ok('a friend who has only joined earns nothing yet', j.waiting === 0 && j.pending === 1 && j.days === 0 && j.invited === 1);
[s, j] = await post('/v1/claim', { device: dev(1) });
ok('nothing can be claimed before the friend uses the app', j.claimed === 0);
[s, j] = await post('/v1/active', { device: dev(99) });
ok('a phone that never redeemed activates nothing', j.ok && j.activated === 0);
[s, j] = await post('/v1/active', { device: dev(2) });
ok('the friend saying "used" at once is not believed: it is early and nothing is released', j.ok && j.activated === 0 && j.early === true);
[s, j] = await post('/v1/status', { device: dev(1) });
ok('an early "used" leaves the reward waiting for the friend', j.waiting === 0 && j.pending === 1);
clock += 17 * HOUR;
[s, j] = await post('/v1/active', { device: dev(2) });
ok('17 hours after joining is still early', j.activated === 0 && j.early === true);
clock += 2 * HOUR;
[s, j] = await post('/v1/active', { device: dev(2) });
ok('19 hours after joining the friend using the app releases the reward', j.ok && j.activated === 1 && !j.early);
[s, j] = await post('/v1/active', { device: dev(2) });
ok('saying it again releases nothing more and is not early', j.ok && j.activated === 0 && !j.early);
[s, j] = await post('/v1/active', { device: dev(2) });
ok('activating twice does nothing more', j.activated === 0);
[s, j] = await post('/v1/status', { device: dev(1) });
ok('inviter sees one reward waiting', j.waiting === 1 && j.pending === 0 && j.days === INVITER_DAYS && j.invited === 1);
[s, j] = await post('/v1/claim', { device: dev(1) });
ok('inviter claims it', j.claimed === 1 && j.days === INVITER_DAYS);
[s, j] = await post('/v1/claim', { device: dev(1) });
ok('claiming twice gives nothing', j.claimed === 0 && j.days === 0);
for (let i = 3; i < 3 + MAX_REWARDED_INVITES + 2; i++) { await post('/v1/redeem', { device: dev(i), code }); clock += 19 * HOUR; await post('/v1/active', { device: dev(i) }); }
[s, j] = await post('/v1/status', { device: dev(1) });
ok('rewards stop after the limit, friends still get theirs', j.waiting === MAX_REWARDED_INVITES - 1 && j.invited === MAX_REWARDED_INVITES + 3);
ok('waiting days follow the tiers (3 at 7 days, then 3 days each)', j.days === 3 * INVITER_DAYS + (MAX_REWARDED_INVITES - 1 - 3) * INVITER_DAYS_LATER);
ok('status tells the app the tier schedule', j.tiers && j.tiers.first === TIER_ONE_COUNT && j.tiers.laterDays === INVITER_DAYS_LATER);
[s, j] = await post('/v1/claim', { device: dev(1) });
ok('claim pays the tiered total and the lifetime total is 46', j.days === 39 && j.days + INVITER_DAYS === 4 * INVITER_DAYS + (MAX_REWARDED_INVITES - 4) * INVITER_DAYS_LATER);
const link = (c, ua) => handle(new Request('https://x.test/i/' + c, { method: 'GET', headers: ua ? { 'user-agent': ua } : {} }), env);
const PLAYBASE = 'https://play.google.com/store/apps/details?id=app.dinacharya.launcher';
let lp = await link(code, 'Mozilla/5.0 (Linux; Android 14) Chrome/126 Mobile Safari/537.36');
ok('the invite link goes straight to Google Play with the code as the referrer', lp.status === 302 && lp.headers.get('location') === PLAYBASE + '&referrer=invite_' + code);
ok('the redirect has no page and is never cached', (await lp.text()) === '' && lp.headers.get('cache-control') === 'no-store');
ok('the open was counted for that code', env.DB.clicks.get(code) && env.DB.clicks.get(code).n === 1);
await link(code.toLowerCase(), 'Chrome');
ok('a code in small letters is the same code', env.DB.clicks.get(code).n === 2);
await link(code, 'WhatsApp/2.24.1 A');
await link(code, 'facebookexternalhit/1.1');
await link(code, 'TelegramBot (like TwitterBot)');
ok('the programs that make a link preview are not counted', env.DB.clicks.get(code).n === 2);
lp = await link(code, 'WhatsApp/2.24.1 A');
ok('a preview program is still sent on to Play (so the preview shows the app)', lp.status === 302 && lp.headers.get('location').includes('referrer=invite_' + code));
await link('ZZZZZZZZ', 'Chrome');
ok('a code that does not exist is not counted', !env.DB.clicks.has('ZZZZZZZZ') && env.DB.clicks.size === 1);
lp = await link('ZZZZZZZZ', 'Chrome');
ok('a code that does not exist still goes to Play', lp.status === 302 && lp.headers.get('location').startsWith(PLAYBASE));
env.DB.down = true;
lp = await link(code, 'Chrome');
ok('a database that is down does not stop the redirect', lp.status === 302 && lp.headers.get('location') === PLAYBASE + '&referrer=invite_' + code);
env.DB.down = false;
const lp2 = await link('%3Cscript%3E', 'Chrome');
ok('a link that is not a code at all is a 404 and shows nothing back', lp2.status === 404 && !(await lp2.text()).includes('<script>'));
const lp3 = await link('abc', 'Chrome');
ok('a short bad code goes to Play without a referrer and is not counted', lp3.status === 302 && lp3.headers.get('location') === PLAYBASE && env.DB.clicks.size === 1);
const lp4 = await link('', 'Chrome');
ok('an empty code is not a link', lp4.status === 404);
ok('playUrl: a full code adds the referrer, anything else does not', playUrl('ABCD2345') === PLAYBASE + '&referrer=invite_ABCD2345' && playUrl('') === PLAYBASE && playUrl('ABC') === PLAYBASE);
ok('isBot knows the preview programs and not a phone browser', isBot('WhatsApp/2.23') && isBot('Mozilla/5.0 (compatible; Googlebot/2.1)') && !isBot('Mozilla/5.0 (Linux; Android 14; Pixel 8) AppleWebKit/537.36 Chrome/126.0 Mobile Safari/537.36') && !isBot('') && !isBot(null));
// ---- check: the app asks whether a code can be used before it offers it (nothing is used by asking)
const invitedBefore = (await post('/v1/status', { device: dev(1) }))[1].invited;
[s, j] = await post('/v1/check', { device: dev(500), code });
ok('check: a real code from another phone is fine', s === 200 && j.ok === true);
[s, j] = await post('/v1/check', { device: dev(500), code: 'ZZZZZZZZ' });
ok('check: an unknown code is refused with the same sentence as redeem', s === 400 && j.error === 'That code is not valid.');
[s, j] = await post('/v1/check', { device: dev(1), code });
ok('check: your own code is refused', s === 400 && j.error === 'You cannot use your own code.');
[s, j] = await post('/v1/check', { device: dev(2), code });
ok('check: a phone that already used a code is refused', s === 400 && j.error === 'This phone has already used an invite code.');
[s, j] = await post('/v1/check', { device: dev(500), code: 'abc' });
ok('check: a short code is refused', s === 400);
[s, j] = await post('/v1/check', { device: 'bad', code });
ok('check: a bad phone id is refused', s === 400);
ok('check: asking uses nothing (the inviter has no new friend)', (await post('/v1/status', { device: dev(1) }))[1].invited === invitedBefore);
[s, j] = await post('/v1/redeem', { device: dev(500), code });
ok('check: the phone can still redeem after asking', s === 200 && j.ok);

// ---- rate limits (a pretend limiter that counts calls per key)
const limiter = max => { const c = new Map(); return { c, async limit({ key }) { const n = (c.get(key) || 0) + 1; c.set(key, n); return { success: n <= max }; } }; };
const post2 = async (e, p, o, ip) => { const r = await handle(new Request('https://x.test' + p, { method: 'POST', headers: ip ? { 'cf-connecting-ip': ip } : {}, body: JSON.stringify(o) }), e); return [r.status, await r.json()]; };
const envL = { DB: env.DB, NOW: env.NOW, LIMITER: limiter(5), STRICT: limiter(3) };
let st = [];
for (let i = 0; i < 5; i++) st.push((await post2(envL, '/v1/redeem', { device: dev(600 + i), code: 'ZZZZZZZZ' }, '1.1.1.1'))[0]);
ok('rate limit: the first 3 tries of redeem from one address are answered, the next are 429', st.slice(0, 3).every(x => x === 400) && st[3] === 429 && st[4] === 429);
[s, j] = await post2(envL, '/v1/redeem', { device: dev(600), code: 'ZZZZZZZZ' }, '1.1.1.1');
ok('rate limit: the 429 says so in a sentence the app can show', s === 429 && j.ok === false && /Too many tries/.test(j.error));
[s, j] = await post2(envL, '/v1/redeem', { device: dev(600), code: 'ZZZZZZZZ' }, '2.2.2.2');
ok('rate limit: another address is not held back', s === 400);
[s, j] = await post2(envL, '/v1/check', { device: dev(600), code: 'ZZZZZZZZ' }, '1.1.1.1');
ok('rate limit: check has its own count for the same address (per route)', s === 400);
for (let i = 0; i < 3; i++) st[i] = (await post2(envL, '/v1/status', { device: dev(1) }, '3.3.3.3'))[0];
ok('rate limit: the general limiter lets 5 through', st.slice(0, 3).every(x => x === 200));
for (let i = 0; i < 4; i++) st[i] = (await post2(envL, '/v1/status', { device: dev(1) }, '3.3.3.3'))[0];
ok('rate limit: and then says 429', st[2] === 429 && st[3] === 429);
const envD = { DB: env.DB, NOW: env.NOW, LIMITER: limiter(0), STRICT: limiter(0) };
const clicksBefore = env.DB.clicks.get(code).n;
const ld = await handle(new Request('https://x.test/i/' + code, { headers: { 'user-agent': 'Chrome', 'cf-connecting-ip': '4.4.4.4' } }), envD);
ok('rate limit: a limited address is still sent to Play, but the open is not counted', ld.status === 302 && ld.headers.get('location').includes('referrer=invite_' + code) && env.DB.clicks.get(code).n === clicksBefore);
const envT = { DB: env.DB, NOW: env.NOW, LIMITER: { async limit() { throw new Error('limiter down'); } }, STRICT: { async limit() { throw new Error('limiter down'); } } };
[s, j] = await post2(envT, '/v1/redeem', { device: dev(700), code: 'ZZZZZZZZ' }, '5.5.5.5');
ok('rate limit: a limiter that fails never blocks anyone', s === 400);
[s, j] = await post2({ DB: env.DB, NOW: env.NOW }, '/v1/redeem', { device: dev(700), code: 'ZZZZZZZZ' });
ok('rate limit: with no limiter at all (a plain deploy or the tests) nothing is limited', s === 400);

[s, j] = await post('/v1/nothing', { device: dev(1) });
ok('unknown path is 404', s === 404);
const r = await handle(new Request('https://x.test/v1/code', { method: 'POST', body: 'x'.repeat(5000) }), env);
ok('a huge body is refused', r.status === 400);
// The click statement against a real SQLite (D1 is SQLite), with the real schema: counts an existing code, ignores any other.
try {
  const { DatabaseSync } = await import('node:sqlite');
  const fs = await import('node:fs');
  const real = new DatabaseSync(':memory:');
  real.exec(fs.readFileSync(new URL('../schema.sql', import.meta.url), 'utf8'));
  real.prepare('INSERT INTO codes (device, code, created) VALUES (?, ?, ?)').run('device-real-000001', 'ABCD2345', 1);
  const stmt = fs.readFileSync(new URL('../src/index.js', import.meta.url), 'utf8').match(/prepare\('(INSERT INTO clicks[^']*)'\)/)[1];
  for (const [c, t] of [['ABCD2345', 10], ['ABCD2345', 20], ['NOSUCHCD', 30]]) real.prepare(stmt).run(c, t, c, t);
  const rows = real.prepare('SELECT code, n, last FROM clicks').all();
  ok('real SQLite: the click statement counts an existing code and ignores any other', rows.length === 1 && rows[0].code === 'ABCD2345' && rows[0].n === 2 && rows[0].last === 20);
} catch (e) {
  if (e && (e.code === 'ERR_UNKNOWN_BUILTIN_MODULE' || /node:sqlite/.test(String(e.message)))) console.log('SKIP real SQLite check (node:sqlite is not in this Node)');
  else { console.log('FAIL real SQLite check: ' + e.message); fails++; }
}
console.log(fails ? fails + ' FAILED' : 'ALL PASS'); process.exit(fails ? 1 : 0);
