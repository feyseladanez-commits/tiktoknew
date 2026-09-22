const express = require('express');
const { v4: uuidv4 } = require('uuid');
const db = require('../db');
const { requireAuth } = require('../middleware/auth');

const router = express.Router();

// ---------------------------------------------------------------------------
// TEST MODE: Chapa is disconnected for now so you can try the rest of the app
// (login, accessibility bubble, username detection) without a payment
// provider set up. Every donation is marked "success" immediately, and no
// money actually moves. To reconnect real payments later, restore the
// version of this file that calls the Chapa API and set CHAPA_SECRET_KEY
// etc. in your environment.
// ---------------------------------------------------------------------------

// POST /api/donations/initiate
// Auth required (fan must be logged in). body: { tiktok_username, amount }
// This is what the Android app calls when the fan taps "Donate" in the bubble.
router.post('/initiate', requireAuth, async (req, res) => {
  const { tiktok_username, amount } = req.body;
  const fanUserId = req.user.id;

  if (!tiktok_username || !amount) {
    return res.status(400).json({ error: 'tiktok_username and amount are required' });
  }
  const numericAmount = Number(amount);
  if (!Number.isFinite(numericAmount) || numericAmount <= 0) {
    return res.status(400).json({ error: 'amount must be a positive number' });
  }

  const cleanUsername = tiktok_username.replace(/^@/, '').toLowerCase();
  const creator = db
    .prepare(
      `SELECT id FROM users WHERE role = 'creator' AND tiktok_username = ?`
    )
    .get(cleanUsername);

  if (!creator) {
    return res.status(404).json({ error: 'No registered creator with that TikTok username' });
  }

  const txRef = `donate-${uuidv4()}`;

  // TEST MODE: insert the donation as already successful instead of calling
  // out to a payment provider.
  db.prepare(
    `INSERT INTO donations (id, tx_ref, from_user_id, to_tiktok_username, amount, currency, status, verified_at)
     VALUES (?, ?, ?, ?, ?, 'ETB', 'success', datetime('now'))`
  ).run(uuidv4(), txRef, fanUserId, cleanUsername, numericAmount);

  // No real checkout page exists yet, so send the app straight back with a
  // flag instead of a Chapa checkout URL.
  res.json({ checkout_url: null, tx_ref: txRef, test_mode: true });
});

// POST /api/donations/webhook
// Not used in test mode (no payment provider is calling this yet). Left in
// place so it's a no-op rather than a 404 if anything hits it.
router.post('/webhook', (req, res) => {
  res.status(200).send('test mode: no payment provider connected');
});

// GET /api/donations/status/:tx_ref
// The Android app polls this after tapping Donate, so it knows when to show
// "Sent!" in the bubble.
router.get('/status/:tx_ref', requireAuth, (req, res) => {
  const row = db
    .prepare(`SELECT status, amount, to_tiktok_username FROM donations WHERE tx_ref = ?`)
    .get(req.params.tx_ref);

  if (!row) {
    return res.status(404).json({ error: 'Unknown tx_ref' });
  }
  res.json(row);
});

module.exports = router;
