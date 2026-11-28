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
export const MIN_ACTIVE_MS = 18 * 3600 * 1000;   // a friend's use of the app counts for the inviter only this long after the friend joined (about "two different days")
const CODE_CHARS = 'ABCDEFGHJKLMNPQRSTUVWXYZ23456789';

const json = (o, status = 200) => new Response(JSON.stringify(o), { status, headers: { 'content-type': 'application/json; charset=utf-8', 'cache-control': 'no-store', 'x-content-type-options': 'nosniff' } });
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

async function codeFor(db, device, now) {
  const row = await db.prepare('SELECT code FROM codes WHERE device = ?').bind(device).first();
  if (row) return row.code;
  for (let i = 0; i < 5; i++) {
    const code = newCode();
    try {
      await db.prepare('INSERT INTO codes (device, code, created) VALUES (?, ?, ?)').bind(device, code, now).run();
      return code;
    } catch (e) {
      const again = await db.prepare('SELECT code FROM codes WHERE device = ?').bind(device).first();
      if (again) return again.code;   // another request made it first; otherwise the code clashed: try a new one
    }
  }
  throw new Error('no code');
}

// Rate limits: Cloudflare's rate-limit bindings (see wrangler.toml), counted per address and route. Nothing is stored by this code.
// A missing or failing limiter never blocks anyone.
async function limited(req, env, binding, route) {
  const l = env[binding];
  if (!l || typeof l.limit !== 'function') return false;
  try {
    const ip = req.headers.get('cf-connecting-ip') || 'unknown';
    const r = await l.limit({ key: route + ':' + ip });
    return !!r && r.success === false;
  } catch (e) { return false; }
}

const PLAY = 'https://play.google.com/store/apps/details?id=app.dinacharya.launcher';

// Where a shared invite link goes: straight to the app's page on Google Play, with the code attached as the install referrer, which Play
// keeps through the install so the app can read it on its first start. Nothing is shown and nothing needs a tap.
export function playUrl(code) {
  return code && code.length === 8 ? PLAY + '&referrer=' + encodeURIComponent('invite_' + code) : PLAY;
}

// The programs that fetch a link to make its preview (WhatsApp, Telegram and the like) are not friends opening it.
export function isBot(ua) {
  return typeof ua === 'string' && /whatsapp|telegrambot|facebookexternalhit|twitterbot|slackbot|discordbot|linkedinbot|skypeuripreview|bot\b|crawler|spider|preview/i.test(ua);
}

// The friend opened the link: count it for that code (only a code that exists; nothing about the friend is kept), then send them on.
// A database that is down must never stop the redirect.
async function openLink(req, env, code, now) {
  if (code.length === 8 && !isBot(req.headers.get('user-agent')) && !(await limited(req, env, 'LIMITER', 'link'))) {
    try {
      await env.DB.prepare('INSERT INTO clicks (code, n, last) SELECT ?, 1, ? WHERE EXISTS (SELECT 1 FROM codes WHERE code = ?) ON CONFLICT(code) DO UPDATE SET n = n + 1, last = ?')
        .bind(code, now, code, now).run();
    } catch (e) { /* the click is not counted; the friend still goes to Play */ }
  }
  return new Response(null, { status: 302, headers: { location: playUrl(code), 'cache-control': 'no-store' } });
}

export async function handle(req, env) {
  const url = new URL(req.url);
  const now = typeof env.NOW === 'function' ? env.NOW() : Date.now();
  if (req.method === 'GET' && url.pathname === '/') return json({ ok: true, service: 'dinacharya-invites' });
  const land = req.method === 'GET' ? url.pathname.match(/^\/i\/([A-Za-z0-9-]{1,16})$/) : null;
  if (land) return openLink(req, env, normaliseCode(land[1]), now);
  if (req.method !== 'POST') return bad('not found', 404);
  const strict = url.pathname === '/v1/redeem' || url.pathname === '/v1/check';   // the two that tell whether a code exists
  if (await limited(req, env, strict ? 'STRICT' : 'LIMITER', url.pathname)) return bad('Too many tries. Please wait a minute and try again.', 429);
  const db = env.DB;
  const b = await body(req);
  if (!b || !validDevice(b.device)) return bad('bad request');

  if (url.pathname === '/v1/code') {
    return json({ ok: true, code: await codeFor(db, b.device, now) });
  }

  // Whether this phone may use this code: the answer is an error sentence, or the code's owner.
  const usable = async () => {
    const code = normaliseCode(b.code);
    if (code.length !== 8) return { error: 'That code is not valid.' };
    const owner = await db.prepare('SELECT device FROM codes WHERE code = ?').bind(code).first();
    if (!owner) return { error: 'That code is not valid.' };
    if (owner.device === b.device) return { error: 'You cannot use your own code.' };
    const used = await db.prepare('SELECT 1 FROM redemptions WHERE friend = ?').bind(b.device).first();
    if (used) return { error: 'This phone has already used an invite code.' };
    return { owner };
  };

  if (url.pathname === '/v1/check') {   // the app asks before it offers a code from an install link: nothing is used here
    const u = await usable();
    return u.error ? bad(u.error) : json({ ok: true });
  }

  if (url.pathname === '/v1/redeem') {
    const u = await usable();
    if (u.error) return bad(u.error);
    const owner = u.owner;
    const count = await db.prepare('SELECT COUNT(*) AS n FROM redemptions WHERE inviter = ?').bind(owner.device).first();
    const rewarded = (count ? count.n : 0) < MAX_REWARDED_INVITES;
    try {
      await db.prepare('INSERT INTO redemptions (friend, inviter, rewarded, claimed, created) VALUES (?, ?, ?, -1, ?)')
        .bind(b.device, owner.device, rewarded ? 1 : 0, now).run();
    } catch (e) { return bad('This phone has already used an invite code.'); }
    return json({ ok: true, days: FRIEND_DAYS });
  }

  // claimed: -1 = the friend joined but has not used the app yet, 0 = waiting for the inviter to collect, 1 = paid out.
  // Every statement is a fixed text; the only things that vary are bound values. Nothing a caller sends is ever part of the SQL text.
  const COUNT_SQL = {
    waiting: 'SELECT COUNT(*) AS n FROM redemptions WHERE inviter = ? AND rewarded = 1 AND claimed = 0',
    paid: 'SELECT COUNT(*) AS n FROM redemptions WHERE inviter = ? AND rewarded = 1 AND claimed = 1',
    pending: 'SELECT COUNT(*) AS n FROM redemptions WHERE inviter = ? AND rewarded = 1 AND claimed = -1',
    invited: 'SELECT COUNT(*) AS n FROM redemptions WHERE inviter = ?',
  };
  const count = async (inviter, which) => {
    const r = await db.prepare(COUNT_SQL[which]).bind(inviter).first();
    return r ? r.n : 0;
  };

  if (url.pathname === '/v1/active') {   // the friend's app says the app has really been used on two different days
    const row = await db.prepare('SELECT created, claimed FROM redemptions WHERE friend = ?').bind(b.device).first();
    if (!row || row.claimed !== -1) return json({ ok: true, activated: 0 });   // never joined, or already counted
    // The app counts the days itself, but a phone that is not the app could say it at once: the server only believes it after a gap.
    if (now - row.created < MIN_ACTIVE_MS) return json({ ok: true, activated: 0, early: true });
    const r = await db.prepare('UPDATE redemptions SET claimed = 0 WHERE friend = ? AND claimed = -1').bind(b.device).run();
    return json({ ok: true, activated: r && r.meta ? r.meta.changes : 0 });
  }

  if (url.pathname === '/v1/status') {
    const waiting = await count(b.device, 'waiting');
    const paid = await count(b.device, 'paid');
    const pending = await count(b.device, 'pending');
    const invited = await count(b.device, 'invited');
    return json({ ok: true, waiting, pending, days: sumDays(paid + 1, waiting), invited, max: MAX_REWARDED_INVITES,
      tiers: { first: TIER_ONE_COUNT, firstDays: INVITER_DAYS, laterDays: INVITER_DAYS_LATER } });
  }

  if (url.pathname === '/v1/claim') {
    const paid = await count(b.device, 'paid');
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
