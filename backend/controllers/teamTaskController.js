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

module.exports = { createTeamTask };
