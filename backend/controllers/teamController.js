// controllers/teamController.js
const teamModel = require('../models/team');
const crypto = require('crypto');

exports.getMembersByTeamId = async (req, res) => {
    try {
        const teamId = req.params.teamId;
        const members = await teamModel.findMembersByTeamId(teamId);
        res.json(members);
    } catch (error) {
        console.error('Error fetching members by team ID:', error);
        res.status(500).json({ error: 'Database error' });
    }
};

exports.joinTeam = async (req, res) => {
    const { userId, inviteCode } = req.body;
    if (!userId || !inviteCode) {
        return res.status(400).json({ message: 'User ID and invite code are required.' });
    }
    try {
        const team = await teamModel.findByInviteCode(inviteCode);
        if (!team) {
            return res.status(404).json({ message: 'Invalid invite code.' });
        }
        const memberData = { id: crypto.randomUUID(), teamId: team.id, userId: userId, role: 'member' };
        await teamModel.addMember(memberData);
        res.status(200).json({ success: true, message: 'Successfully joined team!', team });
    } catch (error) {
        if (error.code === 'ER_DUP_ENTRY') {
            return res.status(409).json({ message: 'You are already in this team.' });
        }
        console.error('Error joining team:', error);
        res.status(500).json({ message: 'An error occurred while trying to join the team.' });
    }
};

exports.getTeamsByUserId = async (req, res) => {
    try {
        const userId = req.params.userId;
        const teams = await teamModel.findTeamsByUserId(userId);
        res.json(teams);
    } catch (error) {
        console.error('Error fetching teams by user ID:', error);
        res.status(500).json({ error: 'Database error' });
    }
};

exports.togglePinTeam = async (req, res) => {
    try {
        const { userId, teamId, isPinned } = req.body;
        await teamModel.updatePinStatus(userId, teamId, isPinned);
        res.json({ success: true, message: 'Pin status updated' });
    } catch (error) {
        console.error('Error toggling pin status:', error);
        res.status(500).json({ error: 'Database error' });
    }
};

exports.createTeam = async (req, res) => {
    try {
        const { name, description, createdBy } = req.body;
        if (!name || !createdBy) {
            return res.status(400).json({ error: 'Missing required fields' });
        }
        const teamId = crypto.randomUUID();
        const memberId = crypto.randomUUID();
        const inviteCode = Math.random().toString(36).substring(2, 8).toUpperCase();
        const newTeam = { id: teamId, name, description, createdBy, inviteCode };
        await teamModel.create(newTeam);
        const memberData = { id: memberId, teamId: teamId, userId: createdBy, role: 'manager' };
        await teamModel.addMember(memberData);
        res.status(201).json(newTeam);
    } catch (error) {
        console.error("Error creating team:", error);
        res.status(500).json({ error: 'Failed to create team' });
    }
};