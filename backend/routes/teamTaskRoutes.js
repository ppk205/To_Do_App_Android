const express = require('express');
const router = express.Router();
const teamTaskController = require('../controllers/teamTaskController');
const { authenticateToken } = require('../middleware/authMiddleware');

// Route tạo team task: POST /api/team-tasks
router.post('/', authenticateToken, teamTaskController.createTeamTask);

// Route lấy danh sách task theo team: GET /api/team-tasks/team/:teamId
router.get('/team/:teamId', authenticateToken, teamTaskController.getTeamTasks);

// Route cập nhật trạng thái task: PATCH /api/team-tasks/:taskId/status
router.patch('/:taskId/status', authenticateToken, teamTaskController.updateTaskStatus);

// Route cập nhật thông tin task (Edit): PUT /api/team-tasks/:taskId
router.put('/:taskId', authenticateToken, teamTaskController.updateTeamTask);

// Route xóa task: DELETE /api/team-tasks/:taskId
router.delete('/:taskId', authenticateToken, teamTaskController.deleteTeamTask);

module.exports = router;