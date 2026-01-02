const pool = require('../config/database');
const { getIO } = require('./realtime');

/**
 * Notification persistence + realtime emission.
 * For now we keep it simple and DB-backed.
 */

async function ensureNotificationsTable(conn) {
  await conn.query(`
    CREATE TABLE IF NOT EXISTS notifications (
      id BIGINT AUTO_INCREMENT PRIMARY KEY,
      userId VARCHAR(100) NOT NULL,
      channel VARCHAR(30) NOT NULL,
      title VARCHAR(255) NOT NULL,
      message TEXT NOT NULL,
      dedupeKey VARCHAR(255) NOT NULL,
      isNew TINYINT(1) NOT NULL DEFAULT 1,
      createdAt BIGINT NOT NULL,
      UNIQUE KEY uniq_user_dedupe (userId, dedupeKey),
      INDEX idx_user_created (userId, createdAt),
      INDEX idx_createdAt (createdAt)
    );
  `);
}

async function createNotification({
  userId,
  channel,
  title,
  message,
  dedupeKey,
  createdAt = Date.now(),
}) {
  const conn = await pool.getConnection();
  try {
    await ensureNotificationsTable(conn);

    await conn.query(
      `INSERT INTO notifications (userId, channel, title, message, dedupeKey, isNew, createdAt)
       VALUES (?, ?, ?, ?, ?, 1, ?)
       ON DUPLICATE KEY UPDATE
         title=VALUES(title),
         message=VALUES(message),
         isNew=1,
         createdAt=GREATEST(notifications.createdAt, VALUES(createdAt))`,
      [userId, channel, title, message, dedupeKey, createdAt]
    );
  } finally {
    conn.release();
  }

  // Emit realtime (best-effort)
  const io = getIO();
  if (io) {
    io.to(`user:${userId}`).emit('notification', {
      userId,
      channel,
      title,
      message,
      dedupeKey,
      createdAt,
    });
  }
}

async function createNotificationsBulk(notifs) {
  for (const n of notifs) {
    // sequential to keep it simple and avoid hammering DB with a big multi-insert
    // (can optimize later)
    await createNotification(n);
  }
}

module.exports = {
  createNotification,
  createNotificationsBulk,
  ensureNotificationsTable,
};

