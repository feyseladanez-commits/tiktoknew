// db.js — SQLite database setup. SQLite is used here to keep the MVP deployable
// with zero external services. Swap for PostgreSQL later by replacing this file
// once you outgrow a single-file database (concurrent writes, multiple servers).

const Database = require('better-sqlite3');
require('dotenv').config();

const db = new Database(process.env.DB_PATH || './data.sqlite');
db.pragma('journal_mode = WAL');

db.exec(`
  CREATE TABLE IF NOT EXISTS users (
    id TEXT PRIMARY KEY,
    email TEXT UNIQUE NOT NULL,
    password_hash TEXT NOT NULL,
    role TEXT NOT NULL CHECK(role IN ('fan', 'creator')),
    tiktok_username TEXT UNIQUE,
    tiktok_verified INTEGER NOT NULL DEFAULT 0,
    created_at TEXT NOT NULL DEFAULT (datetime('now'))
  );

  CREATE TABLE IF NOT EXISTS donations (
    id TEXT PRIMARY KEY,
    tx_ref TEXT UNIQUE NOT NULL,
    from_user_id TEXT NOT NULL,
    to_tiktok_username TEXT NOT NULL,
    amount REAL NOT NULL,
    currency TEXT NOT NULL DEFAULT 'ETB',
    status TEXT NOT NULL DEFAULT 'pending' CHECK(status IN ('pending', 'success', 'failed')),
    created_at TEXT NOT NULL DEFAULT (datetime('now')),
    verified_at TEXT,
    FOREIGN KEY (from_user_id) REFERENCES users(id)
  );

  CREATE INDEX IF NOT EXISTS idx_donations_creator ON donations(to_tiktok_username);
  CREATE INDEX IF NOT EXISTS idx_donations_status ON donations(status);
`);

module.exports = db;
