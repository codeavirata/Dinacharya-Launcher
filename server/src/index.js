// Dinacharya invite server: a tiny Cloudflare Worker with a D1 database.
// A phone gets a personal invite code. A friend who types it in (or installs through the link) redeems it once.
// Both get free Pro days: the friend at once, the inviter when they next ask. The server holds only random device ids and codes.
// No names, emails or phone numbers are stored.

export const FRIEND_DAYS = 7;
export const INVITER_DAYS = 7;
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

export async function handle(req, env) {
  const url = new URL(req.url);
  if (req.method === 'GET' && url.pathname === '/') return json({ ok: true, service: 'dinacharya-invites' });
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
      await db.prepare('INSERT INTO redemptions (friend, inviter, rewarded, claimed, created) VALUES (?, ?, ?, 0, ?)')
        .bind(b.device, owner.device, rewarded ? 1 : 0, Date.now()).run();
    } catch (e) { return bad('This phone has already used an invite code.'); }
    return json({ ok: true, days: FRIEND_DAYS });
  }

  if (url.pathname === '/v1/status') {
    const r = await db.prepare('SELECT COUNT(*) AS n FROM redemptions WHERE inviter = ? AND rewarded = 1 AND claimed = 0').bind(b.device).first();
    const total = await db.prepare('SELECT COUNT(*) AS n FROM redemptions WHERE inviter = ?').bind(b.device).first();
    const waiting = r ? r.n : 0;
    return json({ ok: true, waiting, days: waiting * INVITER_DAYS, invited: total ? total.n : 0, max: MAX_REWARDED_INVITES });
  }

  if (url.pathname === '/v1/claim') {
    const r = await db.prepare('UPDATE redemptions SET claimed = 1 WHERE inviter = ? AND rewarded = 1 AND claimed = 0').bind(b.device).run();
    const n = r && r.meta ? r.meta.changes : 0;
    return json({ ok: true, claimed: n, days: n * INVITER_DAYS });
  }

  return bad('not found', 404);
}

export default {
  async fetch(req, env) {
    try { return await handle(req, env); } catch (e) { return bad('server error', 500); }
  },
};
