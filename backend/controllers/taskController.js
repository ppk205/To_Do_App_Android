const pool = require('../config/database');
const { scheduleForTask } = require('../services/taskDueScheduler');

/**
 * POST /api/tasks/sync
 * Body:
 * {
 *   tasks: [
 *     {
 *       localId: number,
 *       serverId?: string|null,
 *       title: string,
 *       description: string,
 *       deadlineAt?: number|null,
 *       priority: string,
 *       status: string,
 *       tagsCsv?: string,
 *       createdAt?: number,
 *       updatedAt?: number
 *     }
 *   ]
 * }
 */
async function syncTasks(req, res) {
    try {
        const userId = req.user?.id;
        if (!userId) {
            return res.status(401).json({ success: false, message: 'Unauthorized' });
        }

        const tasks = Array.isArray(req.body?.tasks) ? req.body.tasks : [];
        const deletedServerIds = Array.isArray(req.body?.deletedServerIds)
            ? req.body.deletedServerIds.map(String).map(s => s.trim()).filter(Boolean)
            : [];

        // If nothing to sync (no upserts and no deletions)
        if (tasks.length === 0 && deletedServerIds.length === 0) {
            return res.status(200).json({
                success: true,
                message: 'No tasks to sync',
                syncedAt: Date.now(),
                idMap: []
            });
        }

        const conn = await pool.getConnection();
        try {
            await conn.beginTransaction();

            // Ensure table exists (safe for dev). In production, migrations should handle this.
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

            // Apply deletions first
            if (deletedServerIds.length > 0) {
                // chunk to avoid too large IN lists
                const chunkSize = 200;
                for (let i = 0; i < deletedServerIds.length; i += chunkSize) {
                    const chunk = deletedServerIds.slice(i, i + chunkSize);
                    const placeholders = chunk.map(() => '?').join(',');
                    await conn.query(
                        `DELETE FROM Task WHERE userId = ? AND id IN (${placeholders})`,
                        [userId, ...chunk]
                    );
                }
            }

            const idMap = [];

            for (const t of tasks) {
                const localId = t.localId;
                const serverId = (t.serverId && String(t.serverId).trim()) ? String(t.serverId).trim() : null;

                const title = String(t.title || '').trim();
                if (!title) continue;

                const payload = {
                    userId,
                    title,
                    description: t.description || '',
                    deadlineAt: (t.deadlineAt === undefined ? null : t.deadlineAt),
                    priority: t.priority || 'MEDIUM',
                    status: t.status || 'TODO',
                    tagsCsv: t.tagsCsv || '',
                    createdAt: Number.isFinite(t.createdAt) ? t.createdAt : Date.now(),
                    updatedAt: Number.isFinite(t.updatedAt) ? t.updatedAt : Date.now()
                };

                // If client already knows serverId -> upsert by id
                if (serverId) {
                    await conn.query(
                        `INSERT INTO Task (id, userId, title, description, deadlineAt, priority, status, tagsCsv, createdAt, updatedAt)
                         VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                         ON DUPLICATE KEY UPDATE
                           title=VALUES(title),
                           description=VALUES(description),
                           deadlineAt=VALUES(deadlineAt),
                           priority=VALUES(priority),
                           status=VALUES(status),
                           tagsCsv=VALUES(tagsCsv),
                           updatedAt=GREATEST(Task.updatedAt, VALUES(updatedAt))`,
                        [
                            serverId,
                            payload.userId,
                            payload.title,
                            payload.description,
                            payload.deadlineAt,
                            payload.priority,
                            payload.status,
                            payload.tagsCsv,
                            payload.createdAt,
                            payload.updatedAt
                        ]
                    );

                    idMap.push({ localId, serverId });

                    // schedule due notifications (best-effort)
                    await scheduleForTask({
                        userId,
                        taskId: serverId,
                        title: payload.title,
                        deadlineAt: payload.deadlineAt,
                    });
                    continue;
                }

                // No serverId: create a new one
                const [uuidRows] = await conn.query('SELECT UUID() AS id');
                const newId = uuidRows?.[0]?.id;

                await conn.query(
                    `INSERT INTO Task (id, userId, title, description, deadlineAt, priority, status, tagsCsv, createdAt, updatedAt)
                     VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                     ON DUPLICATE KEY UPDATE
                       description=VALUES(description),
                       deadlineAt=VALUES(deadlineAt),
                       priority=VALUES(priority),
                       status=VALUES(status),
                       tagsCsv=VALUES(tagsCsv),
                       updatedAt=GREATEST(Task.updatedAt, VALUES(updatedAt))`,
                    [
                        newId,
                        payload.userId,
                        payload.title,
                        payload.description,
                        payload.deadlineAt,
                        payload.priority,
                        payload.status,
                        payload.tagsCsv,
                        payload.createdAt,
                        payload.updatedAt
                    ]
                );

                idMap.push({ localId, serverId: newId });

                // schedule due notifications (best-effort)
                await scheduleForTask({
                    userId,
                    taskId: newId,
                    title: payload.title,
                    deadlineAt: payload.deadlineAt,
                });
            }

            await conn.commit();

            return res.status(200).json({
                success: true,
                message: 'Synced',
                syncedAt: Date.now(),
                idMap
            });
        } catch (e) {
            await conn.rollback();
            throw e;
        } finally {
            conn.release();
        }
    } catch (error) {
        console.error('syncTasks error:', error);
        res.status(500).json({ success: false, message: 'Server error', error: error.message });
    }
}

/**
 * GET /api/tasks
 * Returns all tasks for current user.
 */
async function listTasks(req, res) {
    try {
        const userId = req.user?.id;
        if (!userId) {
            return res.status(401).json({ success: false, message: 'Unauthorized' });
        }

        const conn = await pool.getConnection();
        try {
            // Ensure table exists (safe for dev)
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

            const [rows] = await conn.query(
                `SELECT id, userId, title, description, deadlineAt, priority, status, tagsCsv, createdAt, updatedAt
                 FROM Task
                 WHERE userId = ?
                 ORDER BY updatedAt DESC`,
                [userId]
            );

            return res.status(200).json({
                success: true,
                tasks: rows || [],
            });
        } finally {
            conn.release();
        }
    } catch (error) {
        console.error('listTasks error:', error);
        return res.status(500).json({ success: false, message: 'Server error', error: error.message });
    }
}

module.exports = {
    syncTasks,
    listTasks,
};
