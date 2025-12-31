const express = require('express');
const router = express.Router();
const teamTaskController = require('../controllers/teamTaskController');
const { authenticateToken } = require('../middleware/authMiddleware');

// Route tạo team task: POST /api/team-tasks
router.post('/', authenticateToken, teamTaskController.createTeamTask);

// Route lấy danh sách task theo team: GET /api/team-tasks/team/:teamId
router.get('/team/:teamId', authenticateToken, teamTaskController.getTeamTasks);

module.exports = router;