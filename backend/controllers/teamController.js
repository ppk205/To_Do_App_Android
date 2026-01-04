// controllers/teamController.js
const teamModel = require('../models/team');
const crypto = require('crypto');
const db = require('../config/database');

// Helper function: Parse tags an toàn
const parseTags = (tagsData) => {
    try {
        if (typeof tagsData === 'string') return JSON.parse(tagsData);
        if (Array.isArray(tagsData)) return tagsData;
        return [];
    } catch (e) {
        return [];
    }
};

// --- 1. Tạo nhóm mới ---
exports.createTeam = async (req, res) => {
    try {
        const { name, description, tags } = req.body;
        const createdBy = req.user ? req.user.id : req.body.createdBy;

        if (!name || !createdBy) {
            return res.status(400).json({ message: 'Missing required fields (name, createdBy)' });
        }

        const teamId = crypto.randomUUID();
        const inviteCode = Math.random().toString(36).substring(2, 8).toUpperCase();

        const newTeam = {
            id: teamId,
            name,
            description,
            tags: tags || [],
            createdBy,
            inviteCode
        };

        // 1. Tạo Team
        await teamModel.create(newTeam);

        // 2. Thêm người tạo làm Manager
        const memberData = {
            id: crypto.randomUUID(),
            teamId: teamId,
            userId: createdBy,
            role: 'manager',
            status: 'active'
        };
        await teamModel.addMember(memberData);

        res.status(201).json(newTeam);
    } catch (error) {
        console.error("Error creating team:", error);
        res.status(500).json({ message: 'Failed to create team' });
    }
};

// --- 2. Lấy danh sách nhóm của tôi ---
exports.getMyTeams = async (req, res) => {
    try {
        const userId = req.user ? req.user.id : req.params.userId;

        const teams = await teamModel.findTeamsByUserId(userId);

        // Parse tags từ JSON string sang mảng object cho frontend
        const formattedTeams = teams.map(team => ({
            ...team,
            tags: parseTags(team.tags)
        }));

        res.json(formattedTeams);
    } catch (error) {
        console.error('Error fetching teams:', error);
        res.status(500).json({ message: 'Database error' });
    }
};

// --- 3. Tham gia nhóm bằng mã mời ---
exports.joinTeam = async (req, res) => {
    const { inviteCode } = req.body;
    const userId = req.user ? req.user.id : req.body.userId;

    if (!userId || !inviteCode) {
        return res.status(400).json({ message: 'User ID and invite code are required.' });
    }

    try {
        // Tìm team
        const team = await teamModel.findByInviteCode(inviteCode);
        if (!team) {
            return res.status(404).json({ message: 'Invalid invite code.' });
        }

        const memberData = {
            id: crypto.randomUUID(),
            teamId: team.id,
            userId: userId,
            role: 'member'
        };

        await teamModel.addMember(memberData);
        res.status(200).json({ success: true, message: 'Joined team successfully', teamId: team.id });

    } catch (error) {
        if (error.code === 'ER_DUP_ENTRY') {
            return res.status(409).json({ message: 'You are already a member of this team.' });
        }
        console.error('Error joining team:', error);
        res.status(500).json({ message: 'An error occurred.' });
    }
};

// --- 4. Ghim / Bỏ ghim Team ---
exports.togglePinTeam = async (req, res) => {
    try {
        const { teamId, isPinned } = req.body;
        const userId = req.user ? req.user.id : req.body.userId;

        await teamModel.updatePinStatus(userId, teamId, isPinned);
        res.json({ success: true, message: 'Pin status updated' });
    } catch (error) {
        console.error('Error toggling pin status:', error);
        res.status(500).json({ message: 'Database error' });
    }
};

// --- 5. Lấy chi tiết Team (Kèm kiểm tra quyền thành viên) ---
exports.getTeamDetail = async (req, res) => {
    try {
        const { teamId } = req.params;
        const userId = req.user ? req.user.id : req.query.userId;

        // 1. Kiểm tra quyền truy cập (User phải là member)
        const membership = await teamModel.findMember(teamId, userId);
        if (!membership) {
            return res.status(403).json({ message: 'Access denied. You are not a member.' });
        }

        // 2. Lấy thông tin team
        const team = await teamModel.findById(teamId);
        if (!team) {
            return res.status(404).json({ message: 'Team not found' });
        }

        // 3. Parse tags
        team.tags = parseTags(team.tags);

        res.json(team);
    } catch (error) {
        console.error('Get Team Detail Error:', error);
        res.status(500).json({ message: 'Server error' });
    }
};

// --- 6. Cập nhật thông tin Team (Chỉ Manager) ---
exports.updateTeam = async (req, res) => {
    try {
        const { teamId } = req.params;
        const { name, description, tags } = req.body;
        const userId = req.user ? req.user.id : req.body.userId;

        // 1. Kiểm tra quyền Manager
        const membership = await teamModel.findMember(teamId, userId);

        if (!membership || membership.role !== 'manager') {
            return res.status(403).json({ message: 'Only manager can update team info' });
        }

        // 2. Cập nhật
        await teamModel.update(teamId, { name, description, tags });

        res.json({ message: 'Team updated successfully' });
    } catch (error) {
        console.error('Update Team Error:', error);
        res.status(500).json({ message: 'Server error' });
    }
};

// --- 7. Các hàm phụ trợ khác (Remove, Handle Request) ---
exports.removeMember = async (req, res) => {
    try {
        const { teamId, userId } = req.body;
        if (!teamId || !userId) {
            return res.status(400).json({ message: 'Missing fields' });
        }
        await teamModel.removeMember(teamId, userId);
        res.json({ success: true, message: 'Member removed' });
    } catch (error) {
        console.error('Error removing member:', error);
        res.status(500).json({ message: 'Database error' });
    }
};

exports.handleJoinRequest = async (req, res) => {
    try {
        const { teamId, userId, action } = req.body;
        // Logic duyệt/từ chối (Pending logic nếu bạn có bảng request riêng,
        // ở đây dùng logic update status trong bảng member)
        if (action === 'approve') {
            await teamModel.updateMemberStatus(teamId, userId, 'active');
            res.json({ success: true, message: 'Member approved' });
        } else if (action === 'reject') {
            await teamModel.removeMember(teamId, userId);
            res.json({ success: true, message: 'Member rejected' });
        } else {
            res.status(400).json({ message: 'Invalid action' });
        }
    } catch (error) {
        console.error('Error handling join request:', error);
        res.status(500).json({ message: 'Database error' });
    }
};

exports.getMembersByTeamId = async (req, res) => {
    try {
        const teamId = req.params.teamId;
        const status = req.query.status || 'active';
        const members = await teamModel.findMembersByTeamId(teamId, status);
        res.json(members);
    } catch (error) {
        console.error('Error fetching members:', error);
        res.status(500).json({ message: 'Database error' });
    }
};

exports.getTeamMessages = async (req, res) => {
    const { teamId } = req.params;

    try {
        // JOIN 3 table: conversations -> messages -> users
        const [messages] = await db.execute(`
            SELECT
            m.id,
            c.team_id as teamId,
            m.sender_id as senderId,
            u.username as senderName,
            u.avatarUrl as senderAvatar,
            m.body as content,
            m.created_at as createdAt
            FROM messages m
            JOIN conversations c ON m.conversation_id = c.id
            LEFT JOIN users u ON m.sender_id = u.id
            WHERE c.team_id = ? AND c.type = 'team'
            ORDER BY m.created_at ASC
            `, [teamId]);

        res.json(messages);
    } catch (error) {
        console.error('Error fetching team messages:', error);
        res.status(500).json({ message: 'Database error' });
    }
};