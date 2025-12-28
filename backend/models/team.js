const db = require('../config/database');

const Team = {
  findTeamsByUserId: async (userId, callback) => {
    try {
      const query = `
        SELECT
            t.*,
            tm.role,
            tm.isPinned,
            sub.memberCount
        FROM
            team t
        JOIN
            teammember tm ON t.id = tm.teamId
        LEFT JOIN
            (SELECT teamId, COUNT(*) AS memberCount FROM teammember GROUP BY teamId) AS sub
        ON
            t.id = sub.teamId
        WHERE
            tm.userId = ?
      `;
      
      const [rows] = await db.query(query, [userId]);
      
      // Chuyển đổi isPinned (số 1/0) thành boolean (true/false)
      const processedRows = rows.map(row => ({
          ...row,
          isPinned: Boolean(row.isPinned)
      }));

      callback(null, processedRows);

    } catch (err) {
      console.error("Database error in findTeamsByUserId:", err);
      callback(err, null);
    }
  },

  updatePinStatus: async (userId, teamId, isPinned, callback) => {
    try {
        const pinValue = isPinned ? 1 : 0;
        const query = 'UPDATE teammember SET isPinned = ? WHERE userId = ? AND teamId = ?';
        const [result] = await db.query(query, [pinValue, userId, teamId]);
        callback(null, result);
    } catch (err) {
        console.error("Database error in updatePinStatus:", err);
        callback(err, null);
    }
  },

  create: async (teamData, callback) => {
    try {
      const { id, name, description, createdBy, inviteCode } = teamData;
      const query = 'INSERT INTO team (id, name, description, createdBy, inviteCode, avatarUrl) VALUES (?, ?, ?, ?, ?, ?)';
      const [result] = await db.query(query, [id, name, description, createdBy, inviteCode, null]);
      callback(null, result);
    } catch (err) {
      console.error("Database error in create:", err);
      callback(err, null);
    }
  },

  addMember: async (memberData, callback) => {
    try {
      const { id, teamId, userId, role } = memberData;
      const query = 'INSERT INTO teammember (id, teamId, userId, role, isPinned) VALUES (?, ?, ?, ?, 0)';
      const [result] = await db.query(query, [id, teamId, userId, role]);
      callback(null, result);
    } catch (err) {
      console.error("Database error in addMember:", err);
      callback(err, null);
    }
  }
};

module.exports = Team;