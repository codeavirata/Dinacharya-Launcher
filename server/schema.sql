CREATE TABLE IF NOT EXISTS codes (
  device  TEXT PRIMARY KEY,
  code    TEXT NOT NULL UNIQUE,
  created INTEGER NOT NULL
);
CREATE TABLE IF NOT EXISTS redemptions (
  friend   TEXT PRIMARY KEY,   -- one invite code per phone, ever
  inviter  TEXT NOT NULL,
  rewarded INTEGER NOT NULL,   -- 0 when the inviter was already over the limit
  claimed  INTEGER NOT NULL,
  created  INTEGER NOT NULL
);
CREATE INDEX IF NOT EXISTS redemptions_inviter ON redemptions (inviter);
-- How many times a shared link was opened, per code (a count and the last time; nothing about who opened it).
CREATE TABLE IF NOT EXISTS clicks (
  code TEXT PRIMARY KEY,
  n    INTEGER NOT NULL,
  last INTEGER NOT NULL
);
