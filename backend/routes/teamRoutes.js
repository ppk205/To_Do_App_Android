// routes/teamRoutes.js
const express = require('express');
const router = express.Router();
const teamController = require('../controllers/teamController');

// Route lấy danh sách team của user
router.get('/user/:userId', teamController.getTeamsByUserId);

// Route lấy danh sách thành viên của một team
router.get('/:teamId/members', teamController.getMembersByTeamId);

// Route TẠO TEAM MỚI
router.post('/create', teamController.createTeam);

// Route Update Pin
router.post('/pin', teamController.togglePinTeam);

// Route Join Team
router.post('/join', teamController.joinTeam);

module.exports = router;