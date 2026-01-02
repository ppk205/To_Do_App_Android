const express = require('express');
const router = express.Router();
const { authenticateToken } = require('../middleware/authMiddleware');
const pool = require('../config/database');
const { ensureNotificationsTable } = require('../services/notificationService');

// GET /api/notifications
router.get('/', authenticateToken, async (req, res) => {
  const userId = req.user?.id;
  if (!userId) return res.status(401).json({ success: false, message: 'Unauthorized' });

  const conn = await pool.getConnection();
  try {
    await ensureNotificationsTable(conn);
    const [rows] = await conn.query(
      `SELECT id, userId, channel, title, message, dedupeKey, isNew, createdAt
       FROM notifications
       WHERE userId = ?
       ORDER BY createdAt DESC
       LIMIT 200`,
      [userId]
    );
    res.json({ success: true, notifications: rows || [] });
  } finally {
    conn.release();
  }
});

// POST /api/notifications/mark-read
router.post('/mark-read', authenticateToken, async (req, res) => {
  const userId = req.user?.id;
  const ids = Array.isArray(req.body?.ids) ? req.body.ids : [];
  if (!userId) return res.status(401).json({ success: false, message: 'Unauthorized' });
  if (ids.length === 0) return res.json({ success: true, updated: 0 });

  const conn = await pool.getConnection();
  try {
    await ensureNotificationsTable(conn);
    const placeholders = ids.map(() => '?').join(',');
    const [result] = await conn.query(
      `UPDATE notifications SET isNew = 0 WHERE userId = ? AND id IN (${placeholders})`,
      [userId, ...ids]
    );
    res.json({ success: true, updated: result.affectedRows || 0 });
  } finally {
    conn.release();
  }
});

// POST /api/notifications/delete
router.post('/delete', authenticateToken, async (req, res) => {
  const userId = req.user?.id;
  const dedupeKey = String(req.body?.dedupeKey || '').trim();
  if (!userId) return res.status(401).json({ success: false, message: 'Unauthorized' });
  if (!dedupeKey) return res.status(400).json({ success: false, message: 'dedupeKey is required' });

  const conn = await pool.getConnection();
  try {
    await ensureNotificationsTable(conn);
    const [result] = await conn.query(
      `DELETE FROM notifications WHERE userId = ? AND dedupeKey = ?`,
      [userId, dedupeKey]
    );
    res.json({ success: true, deleted: result.affectedRows || 0 });
  } finally {
    conn.release();
  }
});

module.exports = router;
