PRAGMA foreign_keys = ON;
CREATE TABLE IF NOT EXISTS accounts (
 id TEXT PRIMARY KEY, qq_id TEXT UNIQUE, wechat_id TEXT UNIQUE,
 display_name TEXT NOT NULL DEFAULT '', avatar_key TEXT,
 password_salt TEXT NOT NULL, password_hash TEXT NOT NULL,
 role TEXT NOT NULL DEFAULT 'user' CHECK(role IN ('user','admin','super_admin')),
 status TEXT NOT NULL DEFAULT 'active' CHECK(status IN ('active','pending','banned')),
 is_whitelisted INTEGER NOT NULL DEFAULT 0 CHECK(is_whitelisted IN (0,1)),
 must_change_password INTEGER NOT NULL DEFAULT 0 CHECK(must_change_password IN (0,1)),
 created_at TEXT NOT NULL, updated_at TEXT NOT NULL, last_login_at TEXT
);
CREATE TABLE IF NOT EXISTS devices (
 id TEXT NOT NULL, account_id TEXT NOT NULL REFERENCES accounts(id) ON DELETE CASCADE,
 device_alias TEXT NOT NULL DEFAULT '', first_seen_at TEXT NOT NULL, last_seen_at TEXT NOT NULL,
 revoked_at TEXT, PRIMARY KEY(account_id,id)
);
CREATE TABLE IF NOT EXISTS sessions (
 id TEXT PRIMARY KEY, account_id TEXT NOT NULL REFERENCES accounts(id) ON DELETE CASCADE,
 device_id TEXT, refresh_hash TEXT NOT NULL, created_at TEXT NOT NULL, expires_at TEXT NOT NULL,
 revoked_at TEXT, last_seen_at TEXT NOT NULL
);
CREATE INDEX IF NOT EXISTS idx_sessions_account ON sessions(account_id);
CREATE INDEX IF NOT EXISTS idx_sessions_refresh_hash ON sessions(refresh_hash);
CREATE TABLE IF NOT EXISTS bans (
 id TEXT PRIMARY KEY, account_id TEXT NOT NULL REFERENCES accounts(id) ON DELETE CASCADE,
 reason TEXT NOT NULL, actor_id TEXT NOT NULL REFERENCES accounts(id),
 created_at TEXT NOT NULL, ended_at TEXT
);
CREATE INDEX IF NOT EXISTS idx_bans_account ON bans(account_id,ended_at);
CREATE TABLE IF NOT EXISTS admin_audit (
 id TEXT PRIMARY KEY, actor_id TEXT NOT NULL REFERENCES accounts(id),
 action TEXT NOT NULL, target_id TEXT, reason TEXT NOT NULL, result TEXT NOT NULL,
 created_at TEXT NOT NULL, request_id TEXT
);
CREATE INDEX IF NOT EXISTS idx_audit_created ON admin_audit(created_at);
CREATE TABLE IF NOT EXISTS settings (key TEXT PRIMARY KEY,value TEXT NOT NULL,updated_at TEXT NOT NULL);
INSERT OR IGNORE INTO settings(key,value,updated_at) VALUES('whitelist_mode','false',datetime('now'));
CREATE TABLE IF NOT EXISTS login_rate_limits (
 key_hash TEXT PRIMARY KEY, window_start INTEGER NOT NULL, attempt_count INTEGER NOT NULL
);


CREATE TABLE IF NOT EXISTS software_catalog (
 id TEXT PRIMARY KEY,
 category TEXT NOT NULL DEFAULT 'other',
 name TEXT NOT NULL,
 publisher TEXT NOT NULL,
 description TEXT NOT NULL DEFAULT '',
 version TEXT NOT NULL,
 architecture TEXT NOT NULL,
 package_type TEXT NOT NULL CHECK(package_type IN ('EXE','MSI','ZIP')),
 source_url TEXT NOT NULL,
 download_url TEXT,
 sha256 TEXT,
 expected_bytes INTEGER NOT NULL DEFAULT 0,
 license TEXT NOT NULL DEFAULT 'unknown',
 redistribution_allowed INTEGER NOT NULL DEFAULT 0 CHECK(redistribution_allowed IN (0,1)),
 min_ram_mb INTEGER NOT NULL DEFAULT 0,
 min_android_api INTEGER NOT NULL DEFAULT 26,
 gpu_notes TEXT NOT NULL DEFAULT '',
 compatibility TEXT NOT NULL DEFAULT '尚未验证',
 icon_url TEXT,
 is_active INTEGER NOT NULL DEFAULT 0 CHECK(is_active IN (0,1)),
 featured_rank INTEGER NOT NULL DEFAULT 0,
 created_at TEXT NOT NULL DEFAULT (datetime('now')),
 updated_at TEXT NOT NULL DEFAULT (datetime('now'))
);
CREATE INDEX IF NOT EXISTS idx_software_catalog_active
 ON software_catalog(is_active,category,featured_rank,name);
