const db = require('../config/database');

const Team = {
  findTeamsByUserId: async (userId) => {
    const query = `
      SELECT t.*, tm.role, tm.isPinned, sub.memberCount
      FROM team t
      JOIN teammember tm ON t.id = tm.teamId
      LEFT JOIN (SELECT teamId, COUNT(*) AS memberCount FROM teammember GROUP BY teamId) AS sub
      ON t.id = sub.teamId
      WHERE tm.userId = ?
    `;
    const [rows] = await db.query(query, [userId]);
    return rows.map(row => ({ ...row, isPinned: Boolean(row.isPinned) }));
  },

  findMembersByTeamId: async (teamId) => {
    const query = `
      SELECT u.id, u.displayName, u.email, u.avatarUrl, tm.role, tm.status
      FROM users u
      JOIN teammember tm ON u.id = tm.userId
      WHERE tm.teamId = ?
      ORDER BY tm.role = 'manager' DESC, u.displayName ASC
    `;
    const [rows] = await db.query(query, [teamId]);
    return rows;
  },

  findByInviteCode: async (inviteCode) => {
    const query = 'SELECT * FROM team WHERE inviteCode = ?';
    const [rows] = await db.query(query, [inviteCode]);
    return rows[0];
  },

  updatePinStatus: (userId, teamId, isPinned) => {
    const pinValue = isPinned ? 1 : 0;
    const query = 'UPDATE teammember SET isPinned = ? WHERE userId = ? AND teamId = ?';
    return db.query(query, [pinValue, userId, teamId]);
  },

  create: (teamData) => {
    const { id, name, description, createdBy, inviteCode } = teamData;
    const query = 'INSERT INTO team (id, name, description, createdBy, inviteCode, avatarUrl) VALUES (?, ?, ?, ?, ?, ?)';
    return db.query(query, [id, name, description, createdBy, inviteCode, null]);
  },

  addMember: (memberData) => {
    const { id, teamId, userId, role } = memberData;
    const query = 'INSERT INTO teammember (id, teamId, userId, role, isPinned) VALUES (?, ?, ?, ?, 0)';
    return db.query(query, [id, teamId, userId, role]);
  }
};

module.exports = Team;