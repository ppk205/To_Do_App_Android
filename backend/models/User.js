const pool = require('../config/database');

class User {
    // Tạo user mới
    static async create(userData) {
        const { id, username, hashedPassword, displayName, email, phone } = userData;

        const query = `
            INSERT INTO users (id, username, hashedPassword, displayName, email, phone)
            VALUES (?, ?, ?, ?, ?, ?)
        `;

        const [result] = await pool.execute(query, [
            id,
            username,
            hashedPassword,
            displayName,
            email,
            phone
        ]);

        return result;
    }

    // Tìm user theo username
    static async findByUsername(username) {
        const query = 'SELECT * FROM users WHERE username = ?';
        const [rows] = await pool.execute(query, [username]);
        return rows[0];
    }

    // Tìm user theo email
    static async findByEmail(email) {
        const query = 'SELECT * FROM users WHERE email = ?';
        const [rows] = await pool.execute(query, [email]);
        return rows[0];
    }

    // Tìm user theo username hoặc email
    static async findByUsernameOrEmail(usernameOrEmail) {
        const query = 'SELECT * FROM users WHERE username = ? OR email = ?';
        const [rows] = await pool.execute(query, [usernameOrEmail, usernameOrEmail]);
        return rows[0];
    }

    // Tìm user theo ID
    static async findById(id) {
        const query = 'SELECT * FROM users WHERE id = ?';
        const [rows] = await pool.execute(query, [id]);
        return rows[0];
    }

    // Kiểm tra username đã tồn tại
    static async isUsernameExists(username) {
        const user = await this.findByUsername(username);
        return !!user;
    }

    // Kiểm tra email đã tồn tại
    static async isEmailExists(email) {
        const user = await this.findByEmail(email);
        return !!user;
    }

    // Update verified status
    static async updateVerified(userId) {
        const query = 'UPDATE users SET verified = 1, updatedAt = CURRENT_TIMESTAMP WHERE id = ?';
        const [result] = await pool.execute(query, [userId]);
        return result;
    }

    // Update password
    static async updatePassword(userId, newPasswordHash) {
        const query = 'UPDATE users SET hashedPassword = ?, updatedAt = CURRENT_TIMESTAMP WHERE id = ?';
        const [result] = await pool.execute(query, [newPasswordHash, userId]);
        return result;
    }

    // Update user (generic)
    static async update(userId, updateData) {
        const fields = [];
        const values = [];

        for (const [key, value] of Object.entries(updateData)) {
            if (key !== 'id') {
                fields.push(`${key} = ?`);
                values.push(value);
            }
        }

        if (fields.length === 0) {
            return { affectedRows: 0 };
        }

        values.push(userId);

        const query = `UPDATE users SET ${fields.join(', ')}, updatedAt = CURRENT_TIMESTAMP WHERE id = ?`;
        const [result] = await pool.execute(query, values);
        return result;
    }
}

module.exports = User;



