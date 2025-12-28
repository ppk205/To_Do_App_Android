// routes/teamRoutes.js
const express = require('express');
const router = express.Router();
const teamController = require('../controllers/teamController');

// Route lấy danh sách team
router.get('/user/:userId', teamController.getTeamsByUserId);

// Route TẠO TEAM MỚI
router.post('/create', teamController.createTeam);

// Route Update Pin
router.post('/pin', teamController.togglePinTeam);

module.exports = router;