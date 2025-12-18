const pool = require('../config/database');
const bcrypt = require('bcryptjs');
const crypto = require('crypto');

class OTP {
    // Tạo OTP mới
    static async create(userId, email, purpose) {
        const otpId = crypto.randomBytes(16).toString('hex');
        const otpCode = Math.floor(100000 + Math.random() * 900000).toString(); // 6-digit OTP
        const otpHash = await bcrypt.hash(otpCode, 10);
        const expiresAt = new Date(Date.now() + 10 * 60 * 1000); // 10 minutes expiry

        const query = `
            INSERT INTO otps (id, userId, email, otpHash, purpose, attempts, used, expiresAt, createdAt)
            VALUES (?, ?, ?, ?, ?, 0, 0, ?, NOW())
        `;

        await pool.execute(query, [otpId, userId, email, otpHash, purpose, expiresAt]);

        return {
            id: otpId,
            otpCode, // Trả về mã OTP để gửi email (chỉ trên backend, không gửi client)
            expiresAt
        };
    }

    // Lấy OTP theo userId và purpose (active và chưa hết hạn)
    static async getActiveOTP(userId, purpose) {
        const query = `
            SELECT * FROM otps
            WHERE userId = ? AND purpose = ? AND used = 0 AND expiresAt > NOW()
            ORDER BY createdAt DESC
            LIMIT 1
        `;

        const [rows] = await pool.execute(query, [userId, purpose]);
        return rows[0] || null;
    }

    // Xác minh OTP
    static async verifyOTP(userId, otpCode, purpose) {
        // Lấy OTP hiện tại
        const otp = await this.getActiveOTP(userId, purpose);

        if (!otp) {
            return {
                success: false,
                message: 'OTP hết hạn hoặc không tồn tại'
            };
        }

        // Kiểm tra attempts
        if (otp.attempts >= 5) {
            return {
                success: false,
                message: 'Vượt quá số lần nhập OTP. Vui lòng yêu cầu OTP mới'
            };
        }

        // So sánh OTP hash
        const isOTPValid = await bcrypt.compare(otpCode, otp.otpHash);

        if (!isOTPValid) {
            // Tăng attempts
            await this.incrementAttempts(otp.id);
            return {
                success: false,
                message: 'OTP không chính xác'
            };
        }

        // OTP chính xác - đánh dấu as used
        await this.markAsUsed(otp.id);

        return {
            success: true,
            message: 'OTP xác thực thành công'
        };
    }

    // Tăng số lần nhập sai
    static async incrementAttempts(otpId) {
        const query = 'UPDATE otps SET attempts = attempts + 1 WHERE id = ?';
        await pool.execute(query, [otpId]);
    }

    // Đánh dấu OTP as used
    static async markAsUsed(otpId) {
        const query = 'UPDATE otps SET used = 1 WHERE id = ?';
        await pool.execute(query, [otpId]);
    }

    // Invalidate OTP cũ (khi resend)
    static async invalidateOldOTPs(userId, purpose) {
        const query = `
            UPDATE otps
            SET used = 1
            WHERE userId = ? AND purpose = ? AND used = 0 AND expiresAt > NOW()
        `;
        await pool.execute(query, [userId, purpose]);
    }

    // Count recent OTPs created within X minutes (for rate limit)
    static async countRecentOTPs(userId, purpose, minutes) {
        const query = `
            SELECT COUNT(*) AS cnt FROM otps
            WHERE userId = ? AND purpose = ? AND createdAt >= DATE_SUB(NOW(), INTERVAL ? MINUTE)
        `;
        const [rows] = await pool.execute(query, [userId, purpose, minutes]);
        return rows[0] ? rows[0].cnt : 0;
    }

    // Delete expired OTPs (cleanup)
    static async deleteExpiredOTPs() {
        const query = 'DELETE FROM otps WHERE expiresAt <= NOW()';
        await pool.execute(query);
    }
}

module.exports = OTP;
