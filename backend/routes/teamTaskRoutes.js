const express = require('express');
const router = express.Router();
const teamTaskController = require('../controllers/teamTaskController');

// Route tạo team task: POST /api/tasks
router.post('/', teamTaskController.createTeamTask);

module.exports = router;