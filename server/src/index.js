// Dinacharya invite server: a tiny Cloudflare Worker with a D1 database.
// A phone gets a personal invite code. A friend who types it in (or installs through the link) redeems it once.
// Both get free Pro days: the friend at once, the inviter when they next ask. The server holds only random device ids and codes.
// No names, emails or phone numbers are stored.

export const FRIEND_DAYS = 7;
export const INVITER_DAYS = 7;          // days for each of the first TIER_ONE_COUNT friends
export const TIER_ONE_COUNT = 4;
export const INVITER_DAYS_LATER = 3;    // days for each rewarded friend after that

// Days the inviter earns for their k-th rewarded friend (1-based). The app shows this schedule.
export function rewardDays(k) { return k <= TIER_ONE_COUNT ? INVITER_DAYS : INVITER_DAYS_LATER; }
function sumDays(from, count) { let d = 0; for (let k = from; k < from + count; k++) d += rewardDays(k); return d; }
export const MAX_REWARDED_INVITES = 10;   // per inviter
const CODE_CHARS = 'ABCDEFGHJKLMNPQRSTUVWXYZ23456789';

const json = (o, status = 200) => new Response(JSON.stringify(o), { status, headers: { 'content-type': 'application/json; charset=utf-8', 'cache-control': 'no-store' } });
const bad = (msg, status = 400) => json({ ok: false, error: msg }, status);

export function validDevice(id) { return typeof id === 'string' && /^[A-Za-z0-9-]{16,64}$/.test(id); }
export function normaliseCode(c) { return typeof c === 'string' ? c.trim().toUpperCase().replace(/[^A-Z0-9]/g, '') : ''; }

function newCode() {
  const b = crypto.getRandomValues(new Uint8Array(8));
  let s = '';
  for (const x of b) s += CODE_CHARS[x % CODE_CHARS.length];
  return s;
}

async function body(req) {
  try {
    const t = await req.text();
    if (t.length > 2000) return null;
    const j = JSON.parse(t);
    return j && typeof j === 'object' ? j : null;
  } catch (e) { return null; }
}

async function codeFor(db, device) {
  const row = await db.prepare('SELECT code FROM codes WHERE device = ?').bind(device).first();
  if (row) return row.code;
  for (let i = 0; i < 5; i++) {
    const code = newCode();
    try {
      await db.prepare('INSERT INTO codes (device, code, created) VALUES (?, ?, ?)').bind(device, code, Date.now()).run();
      return code;
    } catch (e) {
      const again = await db.prepare('SELECT code FROM codes WHERE device = ?').bind(device).first();
      if (again) return again.code;   // another request made it first; otherwise the code clashed: try a new one
    }
  }
  throw new Error('no code');
}

const PLAY = 'https://play.google.com/store/apps/details?id=app.dinacharya.launcher';

// The page a shared invite link opens: the code, a button to get the app, and a button to open it if it is already installed.
export function landing(code) {
  const c = code.length === 8 ? code : '';
  const play = PLAY + (c ? '&referrer=' + encodeURIComponent('invite_' + c) : '');
  const open = c ? 'intent://invite/' + c + '#Intent;scheme=dinacharya;package=app.dinacharya.launcher;end' : '';
  const html = `<!doctype html><html lang="en"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1"><title>Dinacharya Launcher</title>
<style>body{margin:0;font-family:system-ui,sans-serif;background:#0f1115;color:#f2f2f2;display:flex;min-height:100vh;align-items:center;justify-content:center;padding:20px;box-sizing:border-box}
.c{max-width:420px;width:100%;text-align:center}h1{font-size:24px;margin:0 0 6px}p{color:#b8bcc6;line-height:1.5}
.code{font-size:34px;letter-spacing:6px;font-weight:700;background:#1b1f27;border-radius:14px;padding:14px;margin:18px 0 6px}
a.b,button{display:block;width:100%;box-sizing:border-box;margin-top:12px;padding:14px;border-radius:14px;border:0;font-size:16px;font-weight:600;text-decoration:none;cursor:pointer}
a.p{background:#ffb454;color:#1a1204}a.s,button{background:#252a35;color:#f2f2f2}</style></head><body><div class="c">
<h1>Plan your day with Dinacharya</h1><p>A friend invited you. Use their code in the app for <b>7 days of free Pro</b>.</p>
${c ? `<div class="code" id="code">${c}</div><button onclick="navigator.clipboard&&navigator.clipboard.writeText('${c}');this.textContent='Copied'">Copy code</button>
<a class="b p" href="${play}">Get the app on Google Play</a><a class="b s" href="${open}">I already have the app: open it</a>`
: `<a class="b p" href="${play}">Get the app on Google Play</a>`}
<p style="font-size:13px">In the app: Settings &gt; Invite friends &gt; enter the code.</p></div></body></html>`;
  return new Response(html, { status: 200, headers: { 'content-type': 'text/html; charset=utf-8', 'cache-control': 'no-store' } });
}

export async function handle(req, env) {
  const url = new URL(req.url);
  if (req.method === 'GET' && url.pathname === '/') return json({ ok: true, service: 'dinacharya-invites' });
  const land = req.method === 'GET' ? url.pathname.match(/^\/i\/([A-Za-z0-9-]{1,16})$/) : null;
  if (land) return landing(normaliseCode(land[1]));
  if (req.method !== 'POST') return bad('not found', 404);
  const db = env.DB;
  const b = await body(req);
  if (!b || !validDevice(b.device)) return bad('bad request');

  if (url.pathname === '/v1/code') {
    return json({ ok: true, code: await codeFor(db, b.device) });
  }

  if (url.pathname === '/v1/redeem') {
    const code = normaliseCode(b.code);
    if (code.length !== 8) return bad('That code is not valid.');
    const owner = await db.prepare('SELECT device FROM codes WHERE code = ?').bind(code).first();
    if (!owner) return bad('That code is not valid.');
    if (owner.device === b.device) return bad('You cannot use your own code.');
    const used = await db.prepare('SELECT 1 FROM redemptions WHERE friend = ?').bind(b.device).first();
    if (used) return bad('This phone has already used an invite code.');
    const count = await db.prepare('SELECT COUNT(*) AS n FROM redemptions WHERE inviter = ?').bind(owner.device).first();
    const rewarded = (count ? count.n : 0) < MAX_REWARDED_INVITES;
    try {
      await db.prepare('INSERT INTO redemptions (friend, inviter, rewarded, claimed, created) VALUES (?, ?, ?, -1, ?)')
        .bind(b.device, owner.device, rewarded ? 1 : 0, Date.now()).run();
    } catch (e) { return bad('This phone has already used an invite code.'); }
    return json({ ok: true, days: FRIEND_DAYS });
  }

  // claimed: -1 = the friend joined but has not used the app yet, 0 = waiting for the inviter to collect, 1 = paid out.
  const count = async (inviter, where) => {
    const r = await db.prepare('SELECT COUNT(*) AS n FROM redemptions WHERE inviter = ? AND ' + where).bind(inviter).first();
    return r ? r.n : 0;
  };

  if (url.pathname === '/v1/active') {   // the friend's app says the app has really been used
    const r = await db.prepare('UPDATE redemptions SET claimed = 0 WHERE friend = ? AND claimed = -1').bind(b.device).run();
    return json({ ok: true, activated: r && r.meta ? r.meta.changes : 0 });
  }

  if (url.pathname === '/v1/status') {
    const waiting = await count(b.device, 'rewarded = 1 AND claimed = 0');
    const paid = await count(b.device, 'rewarded = 1 AND claimed = 1');
    const pending = await count(b.device, 'rewarded = 1 AND claimed = -1');
    const invited = await count(b.device, '1 = 1');
    return json({ ok: true, waiting, pending, days: sumDays(paid + 1, waiting), invited, max: MAX_REWARDED_INVITES,
      tiers: { first: TIER_ONE_COUNT, firstDays: INVITER_DAYS, laterDays: INVITER_DAYS_LATER } });
  }

  if (url.pathname === '/v1/claim') {
    const paid = await count(b.device, 'rewarded = 1 AND claimed = 1');
    const r = await db.prepare('UPDATE redemptions SET claimed = 1 WHERE inviter = ? AND rewarded = 1 AND claimed = 0').bind(b.device).run();
    const n = r && r.meta ? r.meta.changes : 0;
    return json({ ok: true, claimed: n, days: sumDays(paid + 1, n) });
  }

  return bad('not found', 404);
}

export default {
  async fetch(req, env) {
    try { return await handle(req, env); } catch (e) { return bad('server error', 500); }
  },
};
