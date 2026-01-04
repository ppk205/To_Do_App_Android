const pool = require('../config/database');
const crypto = require('crypto');
const { createNotificationsBulk } = require('../services/notificationService');
const { getIO } = require('../services/realtime');

// --- 1. CREATE TASK ---
const createTeamTask = async (req, res) => {
    // UPDATED: Nhận 'tags' (mảng) thay vì 'tagsCsv'
    const { teamId, title, description, dueDate, priority, assignees, createdBy, tags } = req.body;

    if (!teamId || !title || !createdBy || !Array.isArray(assignees)) {
        return res.status(400).json({ message: 'Missing required fields.' });
    }

    const conn = await pool.getConnection();
    try {
        await conn.beginTransaction();

        const taskId = crypto.randomUUID();
        const createdAt = Date.now();

        // Chuẩn bị tags JSON
        const tagsJson = tags ? JSON.stringify(tags) : JSON.stringify([]);

        // UPDATED: Insert vào cột 'tags'
        const taskQuery = `
            INSERT INTO team_tasks (id, teamId, title, description, dueDate, priority, status, createdBy, createdAt, tags)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
        `;
        await conn.query(taskQuery, [taskId, teamId, title, description, dueDate, priority, 'TODO', createdBy, createdAt, tagsJson]);

        // Insert Assignees
        if (assignees.length > 0) {
            const assigneeValues = assignees.map(userId => [taskId, userId]);
            const assigneeQuery = 'INSERT INTO team_task_assignees (taskId, userId) VALUES ?';
            await conn.query(assigneeQuery, [assigneeValues]);
        }

        await conn.commit();

        // Notifications & Realtime
        const uniqueAssignees = Array.from(new Set(assignees.map(String)));
        const notifs = uniqueAssignees.map((userId) => ({
            userId,
            channel: 'teams',
            title: 'New team task',
            message: `You have been assigned to the task "${title}".`,
            dedupeKey: `teamTask:${taskId}:assigned:${userId}`,
            createdAt,
        }));
        await createNotificationsBulk(notifs);

        const io = getIO();
        if (io) {
            io.to(`team:${teamId}`).emit('teamTaskCreated', {
                teamId,
                taskId,
                title,
                description,
                dueDate,
                priority,
                createdBy,
                createdAt,
                assignees: uniqueAssignees,
                tags: tags || [], // Emit mảng tags
            });
        }

        res.status(201).json({ success: true, message: 'Task created successfully', taskId });

    } catch (error) {
        await conn.rollback();
        console.error('Error creating team task:', error);
        res.status(500).json({ message: 'Failed to create task' });
    } finally {
        conn.release();
    }
};

// --- 2. GET TASKS ---
const getTeamTasks = async (req, res) => {
    const { teamId } = req.params;
    if (!teamId) {
        return res.status(400).json({ message: 'Missing teamId parameter' });
    }

    const conn = await pool.getConnection();
    try {
        const now = Date.now();

        // Auto update OVERDUE
        const updateQuery = `
            UPDATE team_tasks
            SET status = 'OVERDUE'
            WHERE teamId = ?
            AND dueDate < ?
            AND status NOT IN ('DONE', 'COMPLETED', 'OVERDUE')
        `;
        await conn.query(updateQuery, [teamId, now]);

        // UPDATED: Select cột 'tags'
        const query = `
            SELECT
                t.id, t.teamId, t.title, t.description, t.dueDate, t.priority, t.status, t.createdAt, t.createdBy, t.tags,
                u.id as assigneeId, u.username, u.avatarUrl, u.email
            FROM team_tasks t
            LEFT JOIN team_task_assignees ta ON t.id = ta.taskId
            LEFT JOIN users u ON ta.userId = u.id
            WHERE t.teamId = ?
            ORDER BY t.dueDate ASC, t.createdAt DESC
        `;

        const [rows] = await conn.query(query, [teamId]);

        const tasksMap = new Map();

        for (const row of rows) {
            if (!tasksMap.has(row.id)) {
                let parsedTags = [];
                try {
                    // Kiểm tra nếu tags là chuỗi thì parse ra JSON, nếu là null/undefined thì mảng rỗng
                    if (typeof row.tags === 'string') {
                        parsedTags = JSON.parse(row.tags);
                    } else if (Array.isArray(row.tags)) {
                        parsedTags = row.tags;
                    }
                } catch (e) {
                    console.error("Error parsing tags:", e);
                    parsedTags = []; // Fallback nếu dữ liệu trong DB bị lỗi
                }

                tasksMap.set(row.id, {
                    id: row.id,
                    teamId: row.teamId,
                    title: row.title,
                    description: row.description,
                    dueDate: row.dueDate,
                    priority: row.priority,
                    status: row.status,
                    tags: row.tags, // MySQL driver thường tự parse JSON
                    createdAt: row.createdAt,
                    createdBy: row.createdBy,
                    assignees: []
                });
            }

            if (row.assigneeId) {
                const task = tasksMap.get(row.id);
                if (!task.assignees.some(a => a.id === row.assigneeId)) {
                    task.assignees.push({
                        id: row.assigneeId,
                        username: row.username,
                        avatarUrl: row.avatarUrl,
                        email: row.email
                    });
                }
            }
        }

        const tasks = Array.from(tasksMap.values());
        res.json(tasks);

    } catch (error) {
        console.error('Error fetching team tasks:', error);
        res.status(500).json({ message: 'Failed to fetch team tasks' });
    } finally {
        conn.release();
    }
};

// --- 3. UPDATE STATUS (PATCH) ---
const updateTaskStatus = async (req, res) => {
    const { taskId } = req.params;
    const { status } = req.body;

    if (!taskId || !status) {
        return res.status(400).json({ message: 'Missing taskId or status' });
    }

    const allowed = ['TODO', 'IN_PROGRESS', 'DONE', 'COMPLETED', 'OVERDUE'];
    if (!allowed.includes(status.toUpperCase())) {
        return res.status(400).json({ message: 'Invalid status' });
    }

    const conn = await pool.getConnection();
    try {
        const updateQuery = `UPDATE team_tasks SET status = ? WHERE id = ?`;
        const [result] = await conn.query(updateQuery, [status.toUpperCase(), taskId]);

        if (result.affectedRows === 0) {
            return res.status(404).json({ message: 'Task not found' });
        }

        // Emit realtime update (Optional but recommended)
        // Cần lấy teamId để emit đúng room, ở đây làm đơn giản bỏ qua hoặc query lại

        res.status(200).json({ success: true });
    } catch (error) {
        console.error('Error updating task status:', error);
        res.status(500).json({ message: 'Failed to update task status' });
    } finally {
        conn.release();
    }
};

// --- 4. UPDATE FULL TASK (PUT) - MỚI THÊM ---
const updateTeamTask = async (req, res) => {
    const { taskId } = req.params;
    const { title, description, dueDate, priority, assignees, tags } = req.body;

    if (!taskId || !title) {
        return res.status(400).json({ message: 'Missing required fields' });
    }

    const conn = await pool.getConnection();
    try {
        await conn.beginTransaction();

        // 1. Update bảng team_tasks
        const tagsJson = tags ? JSON.stringify(tags) : JSON.stringify([]);
        const updateQuery = `
            UPDATE team_tasks
            SET title = ?, description = ?, dueDate = ?, priority = ?, tags = ?
            WHERE id = ?
        `;

        const [result] = await conn.query(updateQuery, [
            title, description, dueDate, priority, tagsJson, taskId
        ]);

        if (result.affectedRows === 0) {
            await conn.rollback();
            return res.status(404).json({ message: 'Task not found' });
        }

        // 2. Update Assignees (Xóa cũ -> Thêm mới)
        if (Array.isArray(assignees)) {
            // Xóa hết assignee cũ
            await conn.query('DELETE FROM team_task_assignees WHERE taskId = ?', [taskId]);

            // Thêm lại assignee mới
            if (assignees.length > 0) {
                const assigneeValues = assignees.map(userId => [taskId, userId]);
                await conn.query('INSERT INTO team_task_assignees (taskId, userId) VALUES ?', [assigneeValues]);
            }
        }

        await conn.commit();
        res.json({ success: true, message: 'Task updated successfully' });

    } catch (error) {
        await conn.rollback();
        console.error('Error updating task:', error);
        res.status(500).json({ message: 'Failed to update task' });
    } finally {
        conn.release();
    }
};

// --- 5. DELETE TASK (DELETE) - MỚI THÊM ---
const deleteTeamTask = async (req, res) => {
    const { taskId } = req.params;

    if (!taskId) {
        return res.status(400).json({ message: 'Missing taskId' });
    }

    const conn = await pool.getConnection();
    try {
        // Chỉ cần xóa ở bảng team_tasks.
        // Bảng team_task_assignees sẽ tự xóa nhờ ON DELETE CASCADE (đã thiết kế trong DB Schema)
        const deleteQuery = `DELETE FROM team_tasks WHERE id = ?`;
        const [result] = await conn.query(deleteQuery, [taskId]);

        if (result.affectedRows === 0) {
            return res.status(404).json({ message: 'Task not found' });
        }

        res.json({ success: true, message: 'Task deleted successfully' });

    } catch (error) {
        console.error('Error deleting task:', error);
        res.status(500).json({ message: 'Failed to delete task' });
    } finally {
        conn.release();
    }
};

module.exports = {
    createTeamTask,
    getTeamTasks,
    updateTaskStatus,
    updateTeamTask, // Export mới
    deleteTeamTask  // Export mới
};