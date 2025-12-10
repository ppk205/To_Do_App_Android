const pool = require('../config/database');

class User {
    // Tạo user mới
    static async create(userData) {
        const { id, username, hashedPassword, displayName, email, phone } = userData;

        const query = `
            INSERT INTO User (id, username, hashedPassword, displayName, email, phone)
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
        const query = 'SELECT * FROM User WHERE username = ?';
        const [rows] = await pool.execute(query, [username]);
        return rows[0];
    }

    // Tìm user theo email
    static async findByEmail(email) {
        const query = 'SELECT * FROM User WHERE email = ?';
        const [rows] = await pool.execute(query, [email]);
        return rows[0];
    }

    // Tìm user theo username hoặc email
    static async findByUsernameOrEmail(usernameOrEmail) {
        const query = 'SELECT * FROM User WHERE username = ? OR email = ?';
        const [rows] = await pool.execute(query, [usernameOrEmail, usernameOrEmail]);
        return rows[0];
    }

    // Tìm user theo ID
    static async findById(id) {
        const query = 'SELECT * FROM User WHERE id = ?';
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
}

module.exports = User;

