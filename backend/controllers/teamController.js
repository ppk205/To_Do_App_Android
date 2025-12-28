// controllers/teamController.js
const teamModel = require('../models/team');
const crypto = require('crypto');

exports.getTeamsByUserId = (req, res) => {
    const userId = req.params.userId;

    teamModel.findTeamsByUserId(userId, (err, results) => {
        if (err) {
            console.error(err);
            return res.status(500).json({ error: 'Database error' });
        }
        res.json(results);
    });
};

exports.createTeam = (req, res) => {
    const { name, description, createdBy } = req.body;

    if (!name || !createdBy) {
        return res.status(400).json({ error: 'Missing required fields' });
    }

    // Tạo ID
    const teamId = crypto.randomUUID();
    const memberId = crypto.randomUUID();
    const inviteCode = Math.random().toString(36).substring(2, 8).toUpperCase();

    const newTeam = {
        id: teamId,
        name,
        description,
        createdBy,
        inviteCode
    };

    // 1. Tạo Team
    teamModel.create(newTeam, (err, result) => {
        if (err) {
            console.error("Error creating team:", err);
            return res.status(500).json({ error: 'Failed to create team in DB' });
        }

        // 2. Thêm người tạo vào làm Manager
        const memberData = {
            id: memberId,
            teamId: teamId,
            userId: createdBy,
            role: 'manager'
        };

        teamModel.addMember(memberData, (errMember, resultMember) => {
             if (errMember) {
                console.error("Error adding member:", errMember);
                return res.status(500).json({ error: 'Failed to add creator to team' });
            }

            // 3. Trả về kết quả thành công
            res.status(201).json(newTeam);
        });
    });
};