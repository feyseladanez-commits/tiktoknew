const express = require('express');
const db = require('../db');

const router = express.Router();

// GET /api/creators/:username
// Called by the Android overlay every time it detects a username on screen.
// Returns whether that TikTok username has a registered, verified creator account.
router.get('/:username', (req, res) => {
  const username = req.params.username.replace(/^@/, '').toLowerCase();

  const creator = db
    .prepare(
      `SELECT id, tiktok_username, tiktok_verified
       FROM users
       WHERE role = 'creator' AND tiktok_username = ?`
    )
    .get(username);

  if (!creator) {
    return res.status(404).json({ registered: false });
  }

  res.json({
    registered: true,
    verified: !!creator.tiktok_verified,
    tiktok_username: creator.tiktok_username,
  });
});

// GET /api/creators/:username/totals
// Optional: lets a creator (or your admin dashboard) see running donation totals.
router.get('/:username/totals', (req, res) => {
  const username = req.params.username.replace(/^@/, '').toLowerCase();

  const row = db
    .prepare(
      `SELECT COALESCE(SUM(amount), 0) AS total
       FROM donations
       WHERE to_tiktok_username = ? AND status = 'success'`
    )
    .get(username);

  res.json({ tiktok_username: username, total_received: row.total });
});

module.exports = router;
