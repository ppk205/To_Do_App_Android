const { isRedisConnected, redisClient } = require('../config/redis');
const pool = require('../config/database');
const { createNotification } = require('./notificationService');

// Offsets for reminders (ms). Keep minimal + useful.
const OFFSETS = [24 * 60 * 60 * 1000, 60 * 60 * 1000]; // 24h, 1h

function redisKey(userId, taskId, offsetMs) {
  return `due:user:${userId}:task:${taskId}:offset:${offsetMs}`;
}

async function scheduleForTask({ userId, taskId, title, deadlineAt }) {
  if (!deadlineAt || !Number.isFinite(deadlineAt)) return;

  const now = Date.now();
  const conn = await pool.getConnection();
  try {
    // ensure Task table exists already handled elsewhere, but safe.
    await conn.query(`
      CREATE TABLE IF NOT EXISTS Task (
        id CHAR(36) PRIMARY KEY,
        userId VARCHAR(100) NOT NULL,
        title VARCHAR(255) NOT NULL,
        description TEXT,
        deadlineAt BIGINT NULL,
        priority VARCHAR(20) NOT NULL,
        status VARCHAR(30) NOT NULL,
        tagsCsv TEXT,
        createdAt BIGINT NOT NULL,
        updatedAt BIGINT NOT NULL,
        UNIQUE KEY uniq_user_title_created (userId, title, createdAt)
      );
    `);
  } finally {
    conn.release();
  }

  for (const offsetMs of OFFSETS) {
    const fireAt = deadlineAt - offsetMs;
    const dedupeKey = `task:${taskId}:due:${offsetMs}`;

    if (fireAt <= now) {
      // Created close to deadline (or already after reminder time) => notify immediately.
      await createNotification({
        userId,
        channel: 'tasks',
        title: 'Due soon',
        message: `Task "${title}" is due in ${offsetMs === 3600000 ? '1 hour' : '24 hours'}.`,
        dedupeKey,
        createdAt: now,
      });
      continue;
    }

    // Best-effort Redis scheduling: we store a key and run a periodic poller to fire.
    if (typeof isRedisConnected === 'function' && isRedisConnected()) {
      const key = redisKey(userId, taskId, offsetMs);
      // Store payload and let poller handle it.
      await redisClient.hSet(key, {
        userId,
        taskId,
        title,
        deadlineAt: String(deadlineAt),
        offsetMs: String(offsetMs),
        fireAt: String(fireAt),
      });
      // expire after deadline + 1 day
      await redisClient.pExpireAt(key, deadlineAt + 24 * 60 * 60 * 1000);
      await redisClient.zAdd('due_jobs', [{ score: fireAt, value: key }]);
    }
  }
}

async function duePollerTick() {
  if (!(typeof isRedisConnected === 'function' && isRedisConnected())) return;

  const now = Date.now();

  // Fetch up to 200 due jobs per tick.
  const keys = await redisClient.zRangeByScore('due_jobs', 0, now, { LIMIT: { offset: 0, count: 200 } });
  if (!keys || keys.length === 0) return;

  for (const key of keys) {
    try {
      // Remove first to avoid double firing.
      await redisClient.zRem('due_jobs', key);
      const payload = await redisClient.hGetAll(key);
      if (!payload || !payload.userId) continue;

      const userId = payload.userId;
      const taskId = payload.taskId;
      const title = payload.title || 'Task';
      const offsetMs = Number(payload.offsetMs);
      const dedupeKey = `task:${taskId}:due:${offsetMs}`;

      await createNotification({
        userId,
        channel: 'tasks',
        title: 'Due soon',
        message: `Task "${title}" is due in ${offsetMs === 3600000 ? '1 hour' : '24 hours'}.`,
        dedupeKey,
        createdAt: now,
      });

      await redisClient.del(key);
    } catch (e) {
      // If something failed, we don't want the poller to crash.
      // Worst case: next sync will create immediate notification due to dedupe.
      // eslint-disable-next-line no-console
      console.error('duePollerTick job error:', e);
    }
  }
}

function startDuePoller() {
  // tick every 15s
  setInterval(() => {
    duePollerTick().catch((e) => console.error('duePollerTick error:', e));
  }, 15000);
}

module.exports = {
  scheduleForTask,
  startDuePoller,
  duePollerTick,
};
