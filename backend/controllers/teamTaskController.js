const pool = require('../config/database');
const crypto = require('crypto');
const { createNotificationsBulk } = require('../services/notificationService');
const { getIO } = require('../services/realtime');

const createTeamTask = async (req, res) => {
    const { teamId, title, description, dueDate, priority, assignees, createdBy, tags } = req.body;

    if (!teamId || !title || !createdBy || !Array.isArray(assignees)) {
        return res.status(400).json({ message: 'Missing required fields.' });
    }

    const conn = await pool.getConnection();
    try {
        await conn.beginTransaction();

        const taskId = crypto.randomUUID();
        const createdAt = Date.now();

        const taskQuery = `
            INSERT INTO team_tasks (id, teamId, title, description, dueDate, priority, status, createdBy, createdAt, tags)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
        `;

        const tagsJson = tags ? JSON.stringify(tags) : JSON.stringify([]);

        await conn.query(taskQuery, [taskId, teamId, title, description, dueDate, priority, 'TODO', createdBy, createdAt, tagsJson]);

        if (assignees.length > 0) {
            const assigneeValues = assignees.map(userId => [taskId, userId]);
            const assigneeQuery = 'INSERT INTO team_task_assignees (taskId, userId) VALUES ?';
            await conn.query(assigneeQuery, [assigneeValues]);
        }

        await conn.commit();

        // Realtime notify + persist notifications for each assignee
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
                tags: tags || [],
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

const getTeamTasks = async (req, res) => {
    const { teamId } = req.params;
    if (!teamId) {
        return res.status(400).json({ message: 'Missing teamId parameter' });
    }

    const conn = await pool.getConnection();
    try {
        const now = Date.now();

        const updateQuery = `
            UPDATE team_tasks
            SET status = 'OVERDUE'
            WHERE teamId = ?
            AND dueDate < ?
            AND status NOT IN ('DONE', 'COMPLETED', 'OVERDUE')
        `;
        await conn.query(updateQuery, [teamId, now]);

        // Query tasks and their assignees via JOIN
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

        // Group rows by task ID since one task can have multiple assignees
        const tasksMap = new Map();

        for (const row of rows) {
            if (!tasksMap.has(row.id)) {
                tasksMap.set(row.id, {
                    id: row.id,
                    teamId: row.teamId,
                    title: row.title,
                    description: row.description,
                    dueDate: row.dueDate,
                    priority: row.priority,
                    status: row.status,
                    tags: row.tags,
                    createdAt: row.createdAt,
                    createdBy: row.createdBy,
                    assignees: []
                });
            }

            if (row.assigneeId) {
                // Check uniqueness
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

// Update the status of a team task
const updateTaskStatus = async (req, res) => {
    const { taskId } = req.params;
    const { status } = req.body;

    console.log('[teamTaskController] updateTaskStatus called', { taskId, body: req.body, auth: req.headers['authorization'] });

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

        res.status(200).json({ success: true });
    } catch (error) {
        console.error('Error updating task status:', error);
        res.status(500).json({ message: 'Failed to update task status' });
    } finally {
        conn.release();
    }
};

module.exports = { createTeamTask, getTeamTasks, updateTaskStatus };