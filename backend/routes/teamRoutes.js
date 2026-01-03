const express = require('express');
const router = express.Router();
const teamController = require('../controllers/teamController');
const { authenticateToken } = require('../middleware/authMiddleware');

// --- CÁC ROUTE CHỨC NĂNG ---

// 1. Tạo team mới
// POST /teams/create
router.post('/create', authenticateToken, teamController.createTeam);

// 2. Tham gia team bằng mã code
// POST /teams/join
router.post('/join', authenticateToken, teamController.joinTeam);

// 3. Ghim/Bỏ ghim team
// POST /teams/pin
router.post('/pin', authenticateToken, teamController.togglePinTeam);

// 4. Xóa thành viên (Kick)
// POST /teams/remove-member
router.post('/remove-member', authenticateToken, teamController.removeMember);

// 5. Duyệt hoặc Từ chối yêu cầu tham gia
// POST /teams/request
router.post('/request', authenticateToken, teamController.handleJoinRequest);

// 6. Lấy danh sách team của User
// GET /teams/user/:userId
router.get('/user/:userId', authenticateToken, teamController.getMyTeams);


// --- CÁC ROUTE CÓ PARAM DYNAMIC (ID) ---

// 7. Lấy danh sách thành viên trong team - [MỚI BỔ SUNG]
// GET /teams/:teamId/members
router.get('/:teamId/members', authenticateToken, teamController.getMembersByTeamId);

// 8. Cập nhật thông tin team
// PUT /teams/:teamId
router.put('/:teamId', authenticateToken, teamController.updateTeam);

// 9. Lấy chi tiết team
// GET /teams/:teamId
router.get('/:teamId', authenticateToken, teamController.getTeamDetail);

module.exports = router;