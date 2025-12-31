const pool = require('../config/database');
const crypto = require('crypto');

const createTeamTask = async (req, res) => {
    const { teamId, title, description, dueDate, priority, assignees, createdBy } = req.body;

    if (!teamId || !title || !createdBy || !Array.isArray(assignees)) {
        return res.status(400).json({ message: 'Missing required fields.' });
    }

    const conn = await pool.getConnection();
    try {
        await conn.beginTransaction();

        const taskId = crypto.randomUUID();
        const createdAt = Date.now();

        // 1. Insert vào bảng team_tasks
        const taskQuery = `
            INSERT INTO team_tasks (id, teamId, title, description, dueDate, priority, createdBy, createdAt)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?)
        `;
        await conn.query(taskQuery, [taskId, teamId, title, description, dueDate, priority, createdBy, createdAt]);

        // 2. Insert vào bảng team_task_assignees
        if (assignees.length > 0) {
            const assigneeValues = assignees.map(userId => [taskId, userId]);
            const assigneeQuery = 'INSERT INTO team_task_assignees (taskId, userId) VALUES ?';
            await conn.query(assigneeQuery, [assigneeValues]);
        }

        await conn.commit();

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

    // Lấy ID người dùng hiện tại từ middleware auth
    const currentUserId = req.user.id;

    if (!teamId) {
        return res.status(400).json({ message: 'Missing teamId parameter' });
    }

    const conn = await pool.getConnection();
    try {
        // Chỉ lấy những task mà taskId đó tồn tại trong bảng team_task_assignees với userId của bạn
        const query = `
            SELECT
                t.id, t.teamId, t.title, t.description, t.dueDate, t.priority, t.status, t.createdAt, t.createdBy,
                u.id as assigneeId, u.username, u.avatarUrl, u.email
            FROM team_tasks t
            LEFT JOIN team_task_assignees ta ON t.id = ta.taskId
            LEFT JOIN users u ON ta.userId = u.id
            WHERE t.teamId = ?
              AND t.id IN (SELECT taskId FROM team_task_assignees WHERE userId = ?)
            ORDER BY t.dueDate ASC, t.createdAt DESC
        `;

        const [rows] = await conn.query(query, [teamId, currentUserId]);

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

module.exports = { createTeamTask, getTeamTasks };
