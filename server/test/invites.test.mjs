import { handle, landing, FRIEND_DAYS, INVITER_DAYS, INVITER_DAYS_LATER, TIER_ONE_COUNT, MAX_REWARDED_INVITES } from '../src/index.js';

// A pretend D1 that understands exactly the statements the Worker uses.
function fakeDb() {
  const codes = new Map(), reds = new Map();
  return { prepare(sql) {
    let args = [];
    const q = {
      bind(...a) { args = a; return q; },
      async first() {
        if (sql.startsWith('SELECT code FROM codes')) { const r = codes.get(args[0]); return r ? { code: r.code } : null; }
        if (sql.startsWith('SELECT device FROM codes')) { for (const [d, r] of codes) if (r.code === args[0]) return { device: d }; return null; }
        if (sql.startsWith('SELECT 1 FROM redemptions')) return reds.has(args[0]) ? { 1: 1 } : null;
        if (sql.includes('FROM redemptions WHERE inviter = ?')) {
          const m = sql.match(/AND rewarded = 1 AND claimed = (-?\d)/);
          return { n: [...reds.values()].filter(r => r.inviter === args[0] && (!m || (r.rewarded && r.claimed === Number(m[1])))).length };
        }
        throw new Error('first: ' + sql);
      },
      async run() {
        if (sql.startsWith('INSERT INTO codes')) {
          for (const r of codes.values()) if (r.code === args[1]) throw new Error('unique');
          if (codes.has(args[0])) throw new Error('pk'); codes.set(args[0], { code: args[1] }); return {};
        }
        if (sql.startsWith('INSERT INTO redemptions')) { if (reds.has(args[0])) throw new Error('pk'); reds.set(args[0], { inviter: args[1], rewarded: args[2], claimed: -1 }); return {}; }
        if (sql.startsWith('UPDATE redemptions SET claimed = 0 WHERE friend')) { const r = reds.get(args[0]); if (r && r.claimed === -1) { r.claimed = 0; return { meta: { changes: 1 } }; } return { meta: { changes: 0 } }; }
        if (sql.startsWith('UPDATE redemptions SET claimed = 1')) { let n = 0; for (const r of reds.values()) if (r.inviter === args[0] && r.rewarded && r.claimed === 0) { r.claimed = 1; n++; } return { meta: { changes: n } }; }
        throw new Error('run: ' + sql);
      },
    };
    return q;
  } };
}
let fails = 0; const ok = (n, v) => { console.log((v ? 'PASS ' : 'FAIL ') + n); if (!v) fails++; };
const env = { DB: fakeDb() };
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
ok('the friend using the app releases the reward', j.ok && j.activated === 1);
[s, j] = await post('/v1/active', { device: dev(2) });
ok('activating twice does nothing more', j.activated === 0);
[s, j] = await post('/v1/status', { device: dev(1) });
ok('inviter sees one reward waiting', j.waiting === 1 && j.pending === 0 && j.days === INVITER_DAYS && j.invited === 1);
[s, j] = await post('/v1/claim', { device: dev(1) });
ok('inviter claims it', j.claimed === 1 && j.days === INVITER_DAYS);
[s, j] = await post('/v1/claim', { device: dev(1) });
ok('claiming twice gives nothing', j.claimed === 0 && j.days === 0);
for (let i = 3; i < 3 + MAX_REWARDED_INVITES + 2; i++) { await post('/v1/redeem', { device: dev(i), code }); await post('/v1/active', { device: dev(i) }); }
[s, j] = await post('/v1/status', { device: dev(1) });
ok('rewards stop after the limit, friends still get theirs', j.waiting === MAX_REWARDED_INVITES - 1 && j.invited === MAX_REWARDED_INVITES + 3);
ok('waiting days follow the tiers (3 at 7 days, then 3 days each)', j.days === 3 * INVITER_DAYS + (MAX_REWARDED_INVITES - 1 - 3) * INVITER_DAYS_LATER);
ok('status tells the app the tier schedule', j.tiers && j.tiers.first === TIER_ONE_COUNT && j.tiers.laterDays === INVITER_DAYS_LATER);
[s, j] = await post('/v1/claim', { device: dev(1) });
ok('claim pays the tiered total and the lifetime total is 46', j.days === 39 && j.days + INVITER_DAYS === 4 * INVITER_DAYS + (MAX_REWARDED_INVITES - 4) * INVITER_DAYS_LATER);
const lp = await handle(new Request('https://x.test/i/' + code, { method: 'GET' }), env); const html = await lp.text();
ok('the invite link page shows the code and the Play button', lp.status === 200 && html.includes(code) && html.includes('referrer=invite_' + code) && html.includes('scheme=dinacharya'));
const lp2 = await handle(new Request('https://x.test/i/%3Cscript%3E', { method: 'GET' }), env);
ok('a bad link is not shown back as html', lp2.status === 404 || !(await lp2.text()).includes('<script>'));
const lp3 = await handle(new Request('https://x.test/i/abc', { method: 'GET' }), env);
ok('a short bad code still gives a page with the Play button, no code', lp3.status === 200 && (await lp3.text()).includes('play.google.com'));
[s, j] = await post('/v1/nothing', { device: dev(1) });
ok('unknown path is 404', s === 404);
const r = await handle(new Request('https://x.test/v1/code', { method: 'POST', body: 'x'.repeat(5000) }), env);
ok('a huge body is refused', r.status === 400);
console.log(fails ? fails + ' FAILED' : 'ALL PASS'); process.exit(fails ? 1 : 0);
