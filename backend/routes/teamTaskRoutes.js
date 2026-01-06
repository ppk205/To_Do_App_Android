const express = require('express');
const router = express.Router();
const teamTaskController = require('../controllers/teamTaskController');
const { authenticateToken } = require('../middleware/authMiddleware');

// 1. Route tạo team task: POST /api/team-tasks
router.post('/', authenticateToken, teamTaskController.createTeamTask);

// 2. Route lấy danh sách task theo team: GET /api/team-tasks/team/:teamId
router.get('/team/:teamId', authenticateToken, teamTaskController.getTeamTasks);

// 3. Route cập nhật trạng thái task: PATCH /api/team-tasks/:taskId/status
router.patch('/:taskId/status', authenticateToken, teamTaskController.updateTaskStatus);

// 4. Route cập nhật thông tin task (Edit): PUT /api/team-tasks/:taskId
router.put('/:taskId', authenticateToken, teamTaskController.updateTeamTask);

// 5. Route xóa task: DELETE /api/team-tasks/:taskId
router.delete('/:taskId', authenticateToken, teamTaskController.deleteTeamTask);

// 6. Lấy task được giao: GET /api/team-tasks/team/:teamId/assigned
router.get('/team/:teamId/assigned', authenticateToken, teamTaskController.getAssignedTeamTasks);

module.exports = router;

