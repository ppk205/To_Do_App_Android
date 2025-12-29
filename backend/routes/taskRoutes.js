const express = require('express');
const router = express.Router();

const { authenticateToken } = require('../middleware/authMiddleware');
const taskController = require('../controllers/taskController');

// Sync all tasks for current user
router.post('/sync', authenticateToken, taskController.syncTasks);

// List all tasks for current user (sync-down)
router.get('/', authenticateToken, taskController.listTasks);

module.exports = router;

