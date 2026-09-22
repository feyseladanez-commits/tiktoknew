const express = require('express');
const bcrypt = require('bcryptjs');
const jwt = require('jsonwebtoken');
const { v4: uuidv4 } = require('uuid');
const db = require('../db');

const router = express.Router();

function signToken(user) {
  return jwt.sign(
    { id: user.id, role: user.role, email: user.email },
    process.env.JWT_SECRET,
    { expiresIn: '30d' }
  );
}

// POST /api/auth/signup
// body: { email, password, role: "fan" | "creator", tiktok_username (required if role=creator) }
router.post('/signup', async (req, res) => {
  const { email, password, role, tiktok_username } = req.body;

  if (!email || !password || !role) {
    return res.status(400).json({ error: 'email, password and role are required' });
  }
  if (!['fan', 'creator'].includes(role)) {
    return res.status(400).json({ error: 'role must be "fan" or "creator"' });
  }
  if (role === 'creator' && !tiktok_username) {
    return res.status(400).json({ error: 'tiktok_username is required for creator accounts' });
  }

  const existing = db.prepare('SELECT id FROM users WHERE email = ?').get(email);
  if (existing) {
    return res.status(409).json({ error: 'An account with this email already exists' });
  }

  if (role === 'creator') {
    const usernameTaken = db
      .prepare('SELECT id FROM users WHERE tiktok_username = ?')
      .get(tiktok_username.toLowerCase());
    if (usernameTaken) {
      return res.status(409).json({ error: 'This TikTok username is already registered' });
    }
  }

  const passwordHash = await bcrypt.hash(password, 10);
  const id = uuidv4();

  db.prepare(
    `INSERT INTO users (id, email, password_hash, role, tiktok_username, tiktok_verified)
     VALUES (?, ?, ?, ?, ?, ?)`
  ).run(
    id,
    email,
    passwordHash,
    role,
    role === 'creator' ? tiktok_username.toLowerCase() : null,
    0 // NOTE: tiktok_verified starts false. See README for why verification matters
      // before you let this app handle real donations publicly.
  );

  const user = { id, role, email };
  res.status(201).json({ token: signToken(user), user });
});

// POST /api/auth/login
// body: { email, password }
router.post('/login', async (req, res) => {
  const { email, password } = req.body;
  if (!email || !password) {
    return res.status(400).json({ error: 'email and password are required' });
  }

  const row = db.prepare('SELECT * FROM users WHERE email = ?').get(email);
  if (!row) {
    return res.status(401).json({ error: 'Invalid email or password' });
  }

  const valid = await bcrypt.compare(password, row.password_hash);
  if (!valid) {
    return res.status(401).json({ error: 'Invalid email or password' });
  }

  const user = { id: row.id, role: row.role, email: row.email };
  res.json({ token: signToken(user), user });
});

module.exports = router;
