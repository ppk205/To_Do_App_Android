// routes/teamRoutes.js
const express = require('express');
const router = express.Router();
const teamController = require('../controllers/teamController');

// Route lấy danh sách team
router.get('/user/:userId', teamController.getTeamsByUserId);

// Route tạo team
router.post('/create', teamController.createTeam);

module.exports = router;