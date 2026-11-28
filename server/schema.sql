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
