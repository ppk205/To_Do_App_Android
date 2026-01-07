const express = require('express');
const router = express.Router();
const teamController = require('../controllers/teamController');
const { authenticateToken } = require('../middleware/authMiddleware');

// --- CÁC ROUTE CHỨC NĂNG ---

// 1. Tạo team mới
// POST /team/create
router.post('/create', authenticateToken, teamController.createTeam);

// 2. Tham gia team bằng mã code
// POST /team/join
router.post('/join', authenticateToken, teamController.joinTeam);

// 3. Ghim/Bỏ ghim team
// POST /team/pin
router.post('/pin', authenticateToken, teamController.togglePinTeam);

// 4. Xóa thành viên (Kick)
// POST /team/remove-member
router.post('/remove-member', authenticateToken, teamController.removeMember);

// 5. Duyệt hoặc Từ chối yêu cầu tham gia
// POST /team/request
router.post('/request', authenticateToken, teamController.handleJoinRequest);

// 6. Lấy danh sách team của User
// GET /team/user/:userId
router.get('/user/:userId', authenticateToken, teamController.getMyTeams);


// --- CÁC ROUTE CÓ PARAM DYNAMIC (ID) ---

// 7. Lấy danh sách thành viên trong team - [MỚI BỔ SUNG]
// GET /team/:teamId/members
router.get('/:teamId/members', authenticateToken, teamController.getMembersByTeamId);

// 7b. Lấy danh sách leaders (Manager và Co-Manager) - Không cần permission
// GET /team/:teamId/leaders
router.get('/:teamId/leaders', authenticateToken, teamController.getTeamLeaders);

// 8. Cập nhật thông tin team
// PUT /team/:teamId
router.put('/:teamId', authenticateToken, teamController.updateTeam);

// 9. Lấy chi tiết team
// GET /team/:teamId
router.get('/:teamId', authenticateToken, teamController.getTeamDetail);

// 10. Lấy chi tiết tin nhắn của team
// GET /team/:teamId/messages
router.get('/:teamId/messages', authenticateToken, teamController.getTeamMessages);

router.put('/:teamId/invite-code', authenticateToken, teamController.regenerateInviteCode);

router.delete('/:teamId', authenticateToken, teamController.deleteTeam);

// 11. Cập nhật vai trò thành viên trong team
// POST /team/update-member-role
router.post('/update-member-role', authenticateToken, teamController.updateMemberRole);

module.exports = router;