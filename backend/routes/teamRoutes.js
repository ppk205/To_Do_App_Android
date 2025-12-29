// routes/teamRoutes.js
const express = require('express');
const router = express.Router();
const teamController = require('../controllers/teamController');

router.get('/user/:userId', teamController.getTeamsByUserId);
router.get('/:teamId/members', teamController.getMembersByTeamId);
router.post('/create', teamController.createTeam);
router.post('/pin', teamController.togglePinTeam);
router.post('/join', teamController.joinTeam);
router.post('/handle-request', teamController.handleJoinRequest);
router.post('/remove', teamController.removeMember); // API XÓA THÀNH VIÊN

module.exports = router;