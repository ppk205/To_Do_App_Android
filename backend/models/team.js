// models/Team.js
const db = require('../config/database');

const Team = {
  findTeamsByUserId: async (userId) => {
    const query = `
      SELECT t.*, tm.role, tm.isPinned, tm.status, sub.memberCount
      FROM team t
      JOIN teammember tm ON t.id = tm.teamId
      LEFT JOIN (SELECT teamId, COUNT(*) AS memberCount FROM teammember WHERE status = 'active' GROUP BY teamId) AS sub
      ON t.id = sub.teamId
      WHERE tm.userId = ?
      ORDER BY tm.isPinned DESC, t.createdAt DESC
    `;
    const [rows] = await db.query(query, [userId]);
    return rows.map(row => ({ ...row, isPinned: Boolean(row.isPinned) }));
  },

  findMembersByTeamId: async (teamId, status = 'active') => {
    const query = `
      SELECT u.id, u.displayName, u.email, u.avatarUrl, tm.role, tm.status
      FROM users u
      JOIN teammember tm ON u.id = tm.userId
      WHERE tm.teamId = ? AND tm.status = ?
      ORDER BY tm.role = 'manager' DESC, u.displayName ASC
    `;
    const [rows] = await db.query(query, [teamId, status]);
    return rows;
  },

  findByInviteCode: async (inviteCode) => {
    const query = 'SELECT * FROM team WHERE inviteCode = ?';
    const [rows] = await db.query(query, [inviteCode]);
    return rows[0];
  },

  findById: async (teamId) => {
    const query = 'SELECT * FROM team WHERE id = ?';
    const [rows] = await db.query(query, [teamId]);
    return rows[0];
  },

  findMember: async (teamId, userId) => {
    const query = 'SELECT * FROM teammember WHERE teamId = ? AND userId = ?';
    const [rows] = await db.query(query, [teamId, userId]);
    return rows[0];
  },

  updateMemberStatus: (teamId, userId, newStatus) => {
      const query = 'UPDATE teammember SET status = ? WHERE teamId = ? AND userId = ?';
      return db.query(query, [newStatus, teamId, userId]);
  },

  removeMember: (teamId, userId) => {
      const query = 'DELETE FROM teammember WHERE teamId = ? AND userId = ?';
      return db.query(query, [teamId, userId]);
  },

  updatePinStatus: (userId, teamId, isPinned) => {
    const pinValue = isPinned ? 1 : 0;
    const query = 'UPDATE teammember SET isPinned = ? WHERE userId = ? AND teamId = ?';
    return db.query(query, [pinValue, userId, teamId]);
  },

  create: (teamData) => {
    const { id, name, description, tags, createdBy, inviteCode, avatarUrl } = teamData;
    const tagsJson = Array.isArray(tags) ? JSON.stringify(tags) : tags;

    const query = `
        INSERT INTO team (id, name, description, tags, createdBy, inviteCode, avatarUrl, createdAt)
        VALUES (?, ?, ?, ?, ?, ?, ?, NOW())
    `;
    return db.query(query, [id, name, description, tagsJson, createdBy, inviteCode, avatarUrl || null]);
  },

  update: (teamId, teamData) => {
    const { name, description, tags } = teamData;
    const tagsJson = Array.isArray(tags) ? JSON.stringify(tags) : tags;

    const query = 'UPDATE team SET name = ?, description = ?, tags = ? WHERE id = ?';
    return db.query(query, [name, description, tagsJson, teamId]);
  },

  addMember: (memberData) => {
    const { id, teamId, userId, role, status = 'pending' } = memberData;
    const query = 'INSERT INTO teammember (id, teamId, userId, role, status, isPinned, joinedAt) VALUES (?, ?, ?, ?, ?, 0, NOW())';
    return db.query(query, [id, teamId, userId, role, status]);
  }
};

module.exports = Team;