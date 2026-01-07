// controllers/teamController.js
const teamModel = require('../models/team');
const crypto = require('crypto');
const db = require('../config/database');
const { createNotificationsBulk } = require('../services/notificationService');
const { getIO } = require('../services/realtime');

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
        const { name, description, tags, avatarUrl, allowMemberDirectory } = req.body;
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
            inviteCode,
            avatarUrl: avatarUrl || null,
            allowMemberDirectory: allowMemberDirectory ? 1 : 0
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

        // Capture existing active members BEFORE inserting so we can notify them
        let existingMembers = [];
        try {
            existingMembers = await teamModel.findMembersByTeamId(team.id, 'active');
        } catch (_e) {
            existingMembers = [];
        }

        const memberData = {
            id: crypto.randomUUID(),
            teamId: team.id,
            userId: userId,
            role: 'member'
        };

        await teamModel.addMember(memberData);

        // If membership is pending by default, notify managers about the join request.
        // (The model default is status='pending' when not explicitly set.)
        try {
            const [memberRows] = await db.execute(
                'SELECT role, status FROM teammember WHERE teamId = ? AND userId = ? LIMIT 1',
                [team.id, userId]
            );
            const status = String(memberRows?.[0]?.status || '').toLowerCase();

            if (status === 'pending') {
                // Find all active managers
                const [mgrRows] = await db.execute(
                    "SELECT userId FROM teammember WHERE teamId = ? AND role = 'manager' AND status = 'active'",
                    [team.id]
                );
                const managerIds = Array.from(new Set((mgrRows || []).map(r => String(r.userId)).filter(Boolean)));

                if (managerIds.length > 0) {
                    // best-effort joiner display name
                    let joinerName = 'A user';
                    try {
                        const [uRows] = await db.execute('SELECT displayName FROM users WHERE id = ? LIMIT 1', [userId]);
                        if (Array.isArray(uRows) && uRows[0]?.displayName) joinerName = uRows[0].displayName;
                    } catch (_e) {
                    }

                    const createdAt = Date.now();
                    await createNotificationsBulk(
                        managerIds.map((managerId) => ({
                            userId: managerId,
                            channel: 'teams',
                            title: 'Join request',
                            message: `${joinerName} requested to join the team "${team.name}".`,
                            dedupeKey: `team:${team.id}:joinRequest:${userId}`,
                            createdAt,
                        }))
                    );

                    const io = getIO();
                    if (io) {
                        io.to(`team:${team.id}`).emit('team:joinRequest', {
                            teamId: team.id,
                            userId: String(userId),
                            createdAt,
                        });
                    }
                }
            }
        } catch (err) {
            console.error('[teamController] join request notification failed:', err);
        }

        // Persist notifications for existing members (exclude the joiner)
        try {
            const uniqueRecipients = Array.from(
                new Set(
                    (existingMembers || [])
                        .map(m => String(m.id))
                        .filter(id => id && id !== String(userId))
                )
            );

            if (uniqueRecipients.length > 0) {
                // best-effort get joiner display name for message
                let joinerName = 'A new member';
                try {
                    const [rows] = await db.execute('SELECT displayName FROM users WHERE id = ? LIMIT 1', [userId]);
                    if (Array.isArray(rows) && rows[0]?.displayName) joinerName = rows[0].displayName;
                } catch (_e) {
                }

                const createdAt = Date.now();
                await createNotificationsBulk(
                    uniqueRecipients.map((recipientId) => ({
                        userId: recipientId,
                        channel: 'teams',
                        title: 'New team member',
                        message: `${joinerName} joined the team "${team.name}".`,
                        dedupeKey: `team:${team.id}:memberJoined:${userId}`,
                        createdAt,
                    }))
                );
            }

            // Realtime event for anyone currently viewing the team
            const io = getIO();
            if (io) {
                io.to(`team:${team.id}`).emit('team:memberJoined', {
                    teamId: team.id,
                    userId: String(userId),
                    createdAt: Date.now(),
                });
            }
        } catch (err) {
            console.error('[teamController] joinTeam notification failed:', err);
        }

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
        const { name, description, tags, avatarUrl, allowMemberDirectory } = req.body;
        const userId = req.user ? req.user.id : req.body.userId;

        // 1. Kiểm tra quyền Manager
        const membership = await teamModel.findMember(teamId, userId);

        if (!membership || membership.role !== 'manager') {
            return res.status(403).json({ message: 'Only manager can update team info' });
        }

        // 2. Cập nhật - build update object dynamically
        const updateData = {};
        if (name !== undefined) updateData.name = name;
        if (description !== undefined) updateData.description = description;
        if (tags !== undefined) updateData.tags = tags;
        if (avatarUrl !== undefined) updateData.avatarUrl = avatarUrl;
        if (allowMemberDirectory !== undefined) updateData.allowMemberDirectory = allowMemberDirectory ? 1 : 0;

        await teamModel.update(teamId, updateData);

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
        const userId = req.user ? req.user.id : req.query.userId;

        // Check if user is a member of this team
        const membership = await teamModel.findMember(teamId, userId);
        if (!membership) {
            return res.status(403).json({ message: 'Access denied. You are not a member.' });
        }

        // Check if user is manager/co-manager OR if allowMemberDirectory is enabled
        const isManagerOrCoManager = membership.role === 'manager' || membership.role === 'co-manager';

        if (!isManagerOrCoManager) {
            // Check team settings
            const team = await teamModel.findById(teamId);
            if (!team || !team.allowMemberDirectory) {
                return res.status(403).json({ message: 'Access denied. Member directory is disabled.' });
            }
        }

        const members = await teamModel.findMembersByTeamId(teamId, status);
        res.json(members);
    } catch (error) {
        console.error('Error fetching members:', error);
        res.status(500).json({ message: 'Database error' });
    }
};

// Get team leaders (Manager and Co-Manager) - No permission check needed
exports.getTeamLeaders = async (req, res) => {
    try {
        const teamId = req.params.teamId;
        const userId = req.user ? req.user.id : req.query.userId;

        // Only check if user is a member of the team (not permission-based)
        const membership = await teamModel.findMember(teamId, userId);
        if (!membership) {
            return res.status(403).json({ message: 'Access denied. You are not a member.' });
        }

        // Get all active members and filter for managers and co-managers
        const allMembers = await teamModel.findMembersByTeamId(teamId, 'active');
        const leaders = allMembers.filter(member => {
            const role = String(member.role || '').toLowerCase();
            return role === 'manager' || role === 'co-manager';
        });

        res.json(leaders);
    } catch (error) {
        console.error('Error fetching team leaders:', error);
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

exports.regenerateInviteCode = async (req, res) => {
    try {
        const { teamId } = req.params;
        // Logic tạo code ngẫu nhiên
        const newInviteCode = Math.random().toString(36).substring(2, 8).toUpperCase();

        // Cập nhật vào DB (Giờ sẽ gọi hàm update Dynamic, không bị lỗi NULL nữa)
        await teamModel.update(teamId, { inviteCode: newInviteCode });

        // Trả về thông tin team mới nhất
        const updatedTeam = await teamModel.findById(teamId);
        res.json(updatedTeam);
    } catch (error) {
        console.error('Regenerate Code Error:', error);
        res.status(500).json({ message: 'Server error' });
    }
};

// --- 9. Xóa Team (Đã đưa ra ngoài) ---
exports.deleteTeam = async (req, res) => {
    try {
        const { teamId } = req.params;
        const userId = req.user ? req.user.id : req.body.userId;

        const team = await teamModel.findById(teamId);
        if (!team) {
            return res.status(404).json({ message: 'Team not found' });
        }

        if (team.createdBy !== userId) {
            return res.status(403).json({ message: 'Access denied. Only team owner can delete.' });
        }

        await teamModel.delete(teamId);
        res.json({ success: true, message: 'Team deleted successfully' });
    } catch (error) {
        console.error('Delete Team Error:', error);
        res.status(500).json({ message: 'Server error' });
    }
};

// --- 10. Cập nhật vai trò thành viên (Manager, Co-manager) ---
exports.updateMemberRole = async (req, res) => {
    try {
        const { teamId, userId, newRole } = req.body;
        const actorId = req.user ? req.user.id : req.body.actorId;
        if (!teamId || !userId || !newRole) {
            return res.status(400).json({ message: 'Missing fields' });
        }

        // Fetch memberships
        const actor = await teamModel.findMember(teamId, actorId);
        const target = await teamModel.findMember(teamId, userId);
        if (!actor || !target) {
            return res.status(404).json({ message: 'Member not found' });
        }

        const actorRole = String(actor.role || '').toLowerCase();
        const targetRole = String(target.role || '').toLowerCase();
        const desiredRole = String(newRole).toLowerCase();

        // Permissions: manager can toggle member<->co-manager; co-manager can toggle member<->co-manager but cannot change managers.
        const isManager = actorRole === 'manager';
        const isCoManager = actorRole === 'co-manager';

        if (!isManager && !isCoManager) {
            return res.status(403).json({ message: 'No permission' });
        }
        if (targetRole === 'manager') {
            return res.status(403).json({ message: 'Cannot change manager role' });
        }
        if (desiredRole !== 'member' && desiredRole !== 'co-manager') {
            return res.status(400).json({ message: 'Invalid role' });
        }

        await teamModel.updateMemberRole(teamId, userId, desiredRole);
        res.json({ success: true });
    } catch (err) {
        console.error('updateMemberRole error:', err);
        res.status(500).json({ message: 'Server error' });
    }
};

// --- Handle Join Request (Approve/Reject) ---
exports.handleJoinRequest = async (req, res) => {
    const { teamId, userId, action } = req.body;
    const actorId = req.user ? req.user.id : req.body.actorId;

    if (!teamId || !userId || !action) {
        return res.status(400).json({ message: 'teamId, userId, and action are required' });
    }

    if (action !== 'approve' && action !== 'reject') {
        return res.status(400).json({ message: 'Action must be "approve" or "reject"' });
    }

    try {
        // Check if actor is manager or co-manager
        const [actorRows] = await db.execute(
            'SELECT role FROM teammember WHERE teamId = ? AND userId = ? AND status = "active" LIMIT 1',
            [teamId, actorId]
        );

        if (!actorRows || actorRows.length === 0) {
            return res.status(403).json({ message: 'Not a team member' });
        }

        const actorRole = String(actorRows[0].role || '').toLowerCase();
        if (actorRole !== 'manager' && actorRole !== 'co-manager') {
            return res.status(403).json({ message: 'Only managers can handle join requests' });
        }

        // Check if request exists and is pending
        const [requestRows] = await db.execute(
            'SELECT id, status FROM teammember WHERE teamId = ? AND userId = ? LIMIT 1',
            [teamId, userId]
        );

        if (!requestRows || requestRows.length === 0) {
            return res.status(404).json({ message: 'Join request not found' });
        }

        const currentStatus = String(requestRows[0].status || '').toLowerCase();
        if (currentStatus !== 'pending') {
            return res.status(400).json({ message: 'Request is not pending' });
        }

        if (action === 'approve') {
            // Approve: Set status to active
            await db.execute(
                'UPDATE teammember SET status = "active" WHERE teamId = ? AND userId = ?',
                [teamId, userId]
            );

            // Notify the user
            try {
                const [teamRows] = await db.execute('SELECT name FROM team WHERE id = ? LIMIT 1', [teamId]);
                const teamName = teamRows && teamRows[0] ? teamRows[0].name : 'the team';

                const createdAt = Date.now();
                await createNotificationsBulk([{
                    userId: userId,
                    channel: 'teams',
                    title: 'Join request approved',
                    message: `Your request to join "${teamName}" has been approved!`,
                    dedupeKey: `team:${teamId}:joinApproved:${userId}`,
                    createdAt,
                }]);

                // Realtime notification
                const io = getIO();
                if (io) {
                    io.to(`user:${userId}`).emit('notification', {
                        title: 'Join request approved',
                        message: `Your request to join "${teamName}" has been approved!`,
                        createdAt,
                    });
                }
            } catch (notifErr) {
                console.error('[handleJoinRequest] Notification error:', notifErr);
            }

            res.json({ success: true, message: 'Member approved' });
        } else {
            // Reject: Remove the member entry
            await db.execute(
                'DELETE FROM teammember WHERE teamId = ? AND userId = ?',
                [teamId, userId]
            );

            // Notify the user
            try {
                const [teamRows] = await db.execute('SELECT name FROM team WHERE id = ? LIMIT 1', [teamId]);
                const teamName = teamRows && teamRows[0] ? teamRows[0].name : 'the team';

                const createdAt = Date.now();
                await createNotificationsBulk([{
                    userId: userId,
                    channel: 'teams',
                    title: 'Join request rejected',
                    message: `Your request to join "${teamName}" was not approved.`,
                    dedupeKey: `team:${teamId}:joinRejected:${userId}`,
                    createdAt,
                }]);

                // Realtime notification
                const io = getIO();
                if (io) {
                    io.to(`user:${userId}`).emit('notification', {
                        title: 'Join request rejected',
                        message: `Your request to join "${teamName}" was not approved.`,
                        createdAt,
                    });
                }
            } catch (notifErr) {
                console.error('[handleJoinRequest] Notification error:', notifErr);
            }

            res.json({ success: true, message: 'Request rejected' });
        }
    } catch (err) {
        console.error('handleJoinRequest error:', err);
        res.status(500).json({ message: 'Server error' });
    }
};

