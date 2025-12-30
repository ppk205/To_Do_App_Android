const crypto = require('crypto');
const { v4: uuidv4 } = require('uuid');
const pool = require('../config/database');
const User = require('../models/User');
const RedisOTPService = require('./redisOTPService');

/**
 * ========================================
 * PASSWORD RESET SERVICE - OTP Based (OWASP Compliant)
 * ========================================
 *
 * Implements secure OTP-based password reset flow:
 * - 6-digit OTP code
 * - Redis storage with TTL (10 minutes)
 * - Maximum 5 verification attempts
 * - One-time use enforcement
 * - Rate limiting integrated
 * - No user enumeration vulnerability
 * - Revoke all sessions after password change
 */

const OTP_TTL_MINUTES = 10; // OTP expires in 10 minutes
const RESEND_COOLDOWN_SECONDS = 60; // 60 seconds between resend requests

/**
 * Mask email for logging (security)
 */
function maskEmail(email) {
    if (!email || typeof email !== 'string') return '***';
    const parts = email.split('@');
    if (parts.length !== 2) return '***';
    const localPart = parts[0];
    const domain = parts[1];
    const masked = localPart.length > 2
        ? localPart.substring(0, 2) + '***'
        : '***';
    return `${masked}@${domain}`;
}

/**
 * ✅ Generate OTP for password reset
 * @param {string} email - User email
 * @param {Object} requestInfo - { ipAddress, userAgent }
 * @returns {Promise<Object>} - { success, otp?, userId?, message? }
 */
async function generateResetOTP(email, requestInfo = {}) {
    try {
        // Find user by email
        const user = await User.findByEmail(email);

        // ⚠️ SECURITY: Don't reveal if email exists (prevent user enumeration)
        if (!user || user.verified !== 1) {
            console.log(`Password reset requested for non-existent/unverified email: ${maskEmail(email)}`);
            // Return success anyway to prevent enumeration
            return {
                success: true,
                message: 'If the email exists, an OTP has been sent'
            };
        }

        // Use RedisOTPService to create OTP
        // Purpose: 'RESET_PASSWORD', TTL: 10 minutes
        const otpData = await RedisOTPService.createOTP(
            email,
            'RESET_PASSWORD',
            OTP_TTL_MINUTES,
            { userId: user.id, displayName: user.displayName }
        );

        if (!otpData.success) {
            return {
                success: false,
                message: otpData.message || 'Failed to generate OTP'
            };
        }

        console.log(`✅ Password reset OTP generated for user: ${maskEmail(email)}`);

        return {
            success: true,
            otp: otpData.otpCode, // Return OTP to send via email (fixed: otpData.otpCode not otpData.otp)
            userId: user.id,
            displayName: user.displayName,
            email: user.email,
            ttl: otpData.ttl
        };

    } catch (error) {
        console.error('❌ Error generating reset OTP:', error.message);
        throw error;
    }
}

/**
 * ✅ Verify password reset OTP
 * @param {string} email - User email
 * @param {string} otp - 6-digit OTP code
 * @returns {Promise<Object>} - { valid, userId?, message? }
 */
async function verifyResetOTP(email, otp) {
    try {
        // Verify OTP using RedisOTPService
        // deleteOnVerify = false: Keep OTP for final verification in resetPassword()
        const result = await RedisOTPService.verifyOTP(email, otp, 'RESET_PASSWORD', false);

        if (!result.success) {
            return {
                valid: false,
                message: result.message || 'Invalid or expired OTP',
                attemptsLeft: result.attemptsLeft
            };
        }

        // Get user info from pending data
        const userId = result.pendingData?.userId;

        if (!userId) {
            return {
                valid: false,
                message: 'Invalid OTP session'
            };
        }

        return {
            valid: true,
            userId: userId,
            message: 'OTP verified successfully'
        };

    } catch (error) {
        console.error('❌ Error verifying reset OTP:', error.message);
        throw error;
    }
}

/**
 * ✅ Resend password reset OTP
 * @param {string} email - User email
 * @returns {Promise<Object>} - { success, otp?, message? }
 */
async function resendResetOTP(email) {
    try {
        const user = await User.findByEmail(email);

        // ⚠️ SECURITY: Don't reveal if email exists
        if (!user || user.verified !== 1) {
            return {
                success: true,
                message: 'If the email exists, a new OTP has been sent'
            };
        }

        // Resend OTP using RedisOTPService with pendingData
        const result = await RedisOTPService.resendOTP(
            email,
            'RESET_PASSWORD',
            OTP_TTL_MINUTES,
            { userId: user.id, displayName: user.displayName }
        );

        if (!result.success) {
            return {
                success: false,
                message: result.message,
                cooldownRemaining: result.cooldownRemaining
            };
        }

        return {
            success: true,
            otp: result.otpCode,
            displayName: user.displayName,
            email: user.email,
            ttl: result.ttl
        };

    } catch (error) {
        console.error('❌ Error resending reset OTP:', error.message);
        throw error;
    }
}

/**
 * ✅ Reset password after OTP verification
 * @param {string} email - User email
 * @param {string} otp - Verified OTP
 * @param {string} newPasswordHash - New hashed password
 * @returns {Promise<Object>} - { success, message }
 */
async function resetPassword(email, otp, newPasswordHash) {
    const connection = await pool.getConnection();

    try {
        await connection.beginTransaction();

        // Verify OTP one more time
        const verification = await verifyResetOTP(email, otp);

        if (!verification.valid) {
            await connection.rollback();
            return {
                success: false,
                message: verification.message
            };
        }

        const userId = verification.userId;

        // Get user's current password
        const user = await User.findById(userId);

        if (!user) {
            await connection.rollback();
            return {
                success: false,
                message: 'User not found'
            };
        }

        // ✅ SECURITY: Prevent password reuse (optional, can be removed if not needed)
        if (user.hashedPassword === newPasswordHash) {
            await connection.rollback();
            return {
                success: false,
                message: 'New password must be different from the old password'
            };
        }

        // Update password
        const updateQuery = `
            UPDATE users
            SET hashedPassword = ?, updatedAt = CURRENT_TIMESTAMP
            WHERE id = ?
        `;
        await connection.execute(updateQuery, [newPasswordHash, userId]);

        // ✅ CRITICAL SECURITY: Revoke ALL user sessions (force logout everywhere)
        const revokeSessionsQuery = `
            UPDATE user_sessions
            SET revoked = 1, revoked_at = NOW(), revoked_reason = 'PASSWORD_RESET'
            WHERE user_id = ? AND revoked = 0
        `;
        await connection.execute(revokeSessionsQuery, [userId]);

        // Delete OTP and pendingData after successful password reset
        await RedisOTPService.invalidateOTP(email, 'RESET_PASSWORD');
        const pendingKey = RedisOTPService.getPendingRegKey(email);
        await RedisOTPService.clearPendingRegistration(email);

        await connection.commit();

        console.log(`✅ Password reset successful for user: ${user.username}, all sessions revoked`);

        return {
            success: true,
            message: 'Password reset successfully. All sessions have been logged out.',
            user: {
                id: user.id,
                email: user.email,
                displayName: user.displayName
            }
        };

    } catch (error) {
        await connection.rollback();
        console.error('❌ Error resetting password:', error.message);
        throw error;
    } finally {
        connection.release();
    }
}

/**
 * ✅ Get OTP status for debugging
 * @param {string} email
 */
async function getOTPStatus(email) {
    try {
        return await RedisOTPService.getOTPStatus(email, 'RESET_PASSWORD');
    } catch (error) {
        console.error('❌ Error getting OTP status:', error.message);
        throw error;
    }
}

module.exports = {
    generateResetOTP,
    verifyResetOTP,
    resendResetOTP,
    resetPassword,
    getOTPStatus,
    maskEmail
};

