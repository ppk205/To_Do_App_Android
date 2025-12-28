const db = require('../config/database');

const Team = {
  findTeamsByUserId: async (userId, callback) => {
    try {
      // Sử dụng Subquery trong SELECT (Correlated Subquery) - Cách an toàn nhất
      const query = `
        SELECT 
            t.*, 
            tm.role, 
            (SELECT COUNT(*) FROM teammember WHERE teamId = t.id) as memberCount
        FROM team t
        JOIN teammember tm ON t.id = tm.teamId
        WHERE tm.userId = ?
      `;
      
      const [rows] = await db.query(query, [userId]);
      
      // DEBUG: In ra kết quả để kiểm tra
      console.log(`[DEBUG] findTeamsByUserId result for ${userId}:`, rows);

      // Xử lý dữ liệu: Đảm bảo memberCount là số (Number)
      const processedRows = rows.map(row => ({
          ...row,
          memberCount: Number(row.memberCount) || 0 // Ép kiểu sang Number
      }));

      callback(null, processedRows);

    } catch (err) {
      console.error("Database error in findTeamsByUserId:", err);
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
      const query = 'INSERT INTO teammember (id, teamId, userId, role) VALUES (?, ?, ?, ?)';
      const [result] = await db.query(query, [id, teamId, userId, role]);
      callback(null, result);
    } catch (err) {
      console.error("Database error in addMember:", err);
      callback(err, null);
    }
  }
};

module.exports = Team;