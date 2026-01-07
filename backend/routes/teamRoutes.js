const express = require('express');
const router = express.Router();
const teamController = require('../controllers/teamController');
const { authenticateToken } = require('../middleware/authMiddleware');

// 1. Tạo team mới
router.post('/create', authenticateToken, teamController.createTeam);

// 2. Tham gia team bằng mã code
router.post('/join', authenticateToken, teamController.joinTeam);

// 3. Ghim/Bỏ ghim team
router.post('/pin', authenticateToken, teamController.togglePinTeam);

// 4. Xóa thành viên (Kick)
router.post('/remove-member', authenticateToken, teamController.removeMember);

// 5. Duyệt hoặc Từ chối yêu cầu tham gia
router.post('/request', authenticateToken, teamController.handleJoinRequest);

// 6. Lấy danh sách team của User
router.get('/user/:userId', authenticateToken, teamController.getMyTeams);

// 7. Lấy danh sách thành viên trong team
router.get('/:teamId/members', authenticateToken, teamController.getMembersByTeamId);

// 7b. Lấy danh sách leaders (Manager và Co-Manager) - Không cần permission
// GET /team/:teamId/leaders
router.get('/:teamId/leaders', authenticateToken, teamController.getTeamLeaders);

// 8. Cập nhật thông tin team
router.put('/:teamId', authenticateToken, teamController.updateTeam);

// 9. Lấy chi tiết team
router.get('/:teamId', authenticateToken, teamController.getTeamDetail);

// 10. Lấy chi tiết tin nhắn của team
router.get('/:teamId/messages', authenticateToken, teamController.getTeamMessages);

// 11. Regenerate invite code
router.put('/:teamId/invite-code', authenticateToken, teamController.regenerateInviteCode);

// 12. Xóa team
router.delete('/:teamId', authenticateToken, teamController.deleteTeam);

// 13. Cập nhật vai trò thành viên trong team
router.post('/update-member-role', authenticateToken, teamController.updateMemberRole);

module.exports = router;


