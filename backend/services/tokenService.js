const jwt = require('jsonwebtoken');
const crypto = require('crypto');
const { v4: uuidv4 } = require('uuid');
const pool = require('../config/database');

/**
 * ========================================
 * TOKEN SERVICE - OWASP MASTG Compliant
 * ========================================
 */

// Validate JWT secrets at startup
const JWT_SECRET = process.env.JWT_SECRET;
const JWT_REFRESH_SECRET = process.env.REFRESH_TOKEN_SECRET || process.env.JWT_SECRET;

if (!JWT_SECRET || !JWT_REFRESH_SECRET) {
    throw new Error('CRITICAL: JWT_SECRET and REFRESH_TOKEN_SECRET must be defined in environment variables');
}

const ACCESS_TTL_SECONDS = parseInt(process.env.ACCESS_TOKEN_TTL_SECONDS || String(30 * 60), 10); // 30min
const REFRESH_TTL_SECONDS = parseInt(process.env.REFRESH_TOKEN_TTL_SECONDS || String(30 * 24 * 60 * 60), 10); // 30d
const TOKEN_PEPPER = process.env.REFRESH_TOKEN_PEPPER || process.env.OTP_HMAC_SECRET || 'default-pepper-change-in-prod';

/**
 * Generate secure random ID
 */
function randomId(bytes = 16) {
    return crypto.randomBytes(bytes).toString('hex');
}

/**
 * Hash refresh token with pepper for storage
 */
function hashRefreshToken(refreshToken) {
    return crypto.createHmac('sha256', TOKEN_PEPPER)
        .update(refreshToken)
        .digest('hex');
}

/**
 * Create session fingerprint from server-side data
 * Binds session to client characteristics to detect token theft
 */
function createSessionFingerprint(req) {
    const components = [
        req.headers['user-agent'] || '',
        req.headers['accept-language'] || '',
        req.ip || req.connection?.remoteAddress || ''
    ].join('|');

    return crypto.createHash('sha256').update(components).digest('hex');
}

/**
 * Validate session fingerprint
 */
function validateFingerprint(req, storedFingerprint) {
    if (!storedFingerprint) return true; // Backward compatibility
    const currentFingerprint = createSessionFingerprint(req);
    return currentFingerprint === storedFingerprint;
}

/**
 * Issue new session with access + refresh tokens
 * @param {Object} user - User object { id, username, email }
 * @param {Object} deviceInfo - { deviceId, deviceName, ipAddress, userAgent }
 * @returns {Promise<Object>} - { sessionId, accessToken, refreshToken, accessTTL, refreshTTL }
 */
async function issueTokens(user, deviceInfo = {}) {
    const sessionId = uuidv4();
    const jti = uuidv4(); // unique ID for access token
    const tid = uuidv4(); // unique ID for refresh token

    const now = Date.now();
    const accessExpiry = Math.floor(now / 1000) + ACCESS_TTL_SECONDS;
    const refreshExpiry = Math.floor(now / 1000) + REFRESH_TTL_SECONDS;

    // Access token payload (short-lived, stateless)
    const accessPayload = {
        sub: user.id,
        email: user.email,
        username: user.username,
        sid: sessionId,
        jti: jti,
        iat: Math.floor(now / 1000),
        exp: accessExpiry
    };

    // Refresh token payload (long-lived, bound to session)
    const refreshPayload = {
        sub: user.id,
        sid: sessionId,
        tid: tid,
        iat: Math.floor(now / 1000),
        exp: refreshExpiry
    };

    const accessToken = jwt.sign(accessPayload, JWT_SECRET, { noTimestamp: true });
    const refreshToken = jwt.sign(refreshPayload, JWT_REFRESH_SECRET, { noTimestamp: true });

    // Hash refresh token for storage
    const refreshTokenHash = hashRefreshToken(refreshToken);

    // Create session record in database
    const refreshExpiresAt = new Date(refreshExpiry * 1000);
    const issuedAt = new Date(now);

    try {
        await pool.execute(
            `INSERT INTO user_sessions
            (id, user_id, device_id, device_name, refresh_token_hash, access_token_jti,
             refresh_expires_at, issued_at, last_seen_at, ip_address, user_agent)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)`,
            [
                sessionId,
                user.id,
                deviceInfo.deviceId || null,
                deviceInfo.deviceName || null,
                refreshTokenHash,
                jti,
                refreshExpiresAt,
                issuedAt,
                issuedAt,
                deviceInfo.ipAddress || null,
                deviceInfo.userAgent || null
            ]
        );

        console.log(`✅ Session created: ${sessionId} for user ${user.id}`);

        return {
            sessionId,
            accessToken,
            refreshToken,
            accessTTL: ACCESS_TTL_SECONDS,
            refreshTTL: REFRESH_TTL_SECONDS
        };
    } catch (error) {
        console.error('❌ Error creating session:', error);
        throw new Error('Failed to create session');
    }
}

/**
 * Validate and refresh tokens (with rotation)
 * @param {string} refreshToken - Current refresh token
 * @param {Object} deviceInfo - { ipAddress, userAgent }
 * @returns {Promise<Object>} - { accessToken, refreshToken, accessTTL, refreshTTL } or throws error
 */
async function refreshTokens(refreshToken, deviceInfo = {}) {
    let payload;
    try {
        payload = jwt.verify(refreshToken, JWT_REFRESH_SECRET);
    } catch (error) {
        throw new Error('INVALID_TOKEN');
    }

    const { sid, tid, sub } = payload;
    const conn = await pool.getConnection();

    try {
        await conn.beginTransaction();

        // Lock session row for update (atomic operation)
        const [sessions] = await conn.execute(
            `SELECT * FROM user_sessions WHERE id = ? FOR UPDATE`,
            [sid]
        );

        if (!sessions || sessions.length === 0) {
            await conn.rollback();
            throw new Error('SESSION_NOT_FOUND');
        }

        const session = sessions[0];

        // Check if session is revoked
        if (session.revoked) {
            await conn.rollback();
            throw new Error('SESSION_REVOKED');
        }

        // Check if session expired
        if (new Date(session.refresh_expires_at) < new Date()) {
            await conn.rollback();
            throw new Error('SESSION_EXPIRED');
        }

        // Validate refresh token hash (replay detection)
        const presentedHash = hashRefreshToken(refreshToken);
        const storedHash = session.refresh_token_hash;

        if (!crypto.timingSafeEqual(Buffer.from(presentedHash), Buffer.from(storedHash))) {
            // Token reuse detected - revoke session immediately
            await conn.execute(
                `UPDATE user_sessions SET revoked = 1, revoked_at = NOW(), revoked_reason = ? WHERE id = ?`,
                ['TOKEN_REUSE_DETECTED', sid]
            );
            await conn.commit();
            console.warn(`⚠️ Token reuse detected for session ${sid}, user ${sub}. Session revoked.`);
            throw new Error('TOKEN_REUSE_DETECTED');
        }

        // Issue new tokens (rotation)
        const now = Date.now();
        const newJti = uuidv4();
        const newTid = uuidv4();
        const accessExpiry = Math.floor(now / 1000) + ACCESS_TTL_SECONDS;
        const refreshExpiry = Math.floor(now / 1000) + REFRESH_TTL_SECONDS;

        const accessPayload = {
            sub: session.user_id,
            sid: sid,
            jti: newJti,
            iat: Math.floor(now / 1000),
            exp: accessExpiry
        };

        const refreshPayload = {
            sub: session.user_id,
            sid: sid,
            tid: newTid,
            iat: Math.floor(now / 1000),
            exp: refreshExpiry
        };

        const newAccessToken = jwt.sign(accessPayload, JWT_SECRET, { noTimestamp: true });
        const newRefreshToken = jwt.sign(refreshPayload, JWT_REFRESH_SECRET, { noTimestamp: true });

        const newRefreshHash = hashRefreshToken(newRefreshToken);
        const newRefreshExpiresAt = new Date(refreshExpiry * 1000);

        // Update session with new refresh token hash
        await conn.execute(
            `UPDATE user_sessions
             SET refresh_token_hash = ?, access_token_jti = ?, refresh_expires_at = ?,
                 last_seen_at = NOW(), ip_address = ?, user_agent = ?
             WHERE id = ?`,
            [
                newRefreshHash,
                newJti,
                newRefreshExpiresAt,
                deviceInfo.ipAddress || session.ip_address,
                deviceInfo.userAgent || session.user_agent,
                sid
            ]
        );

        await conn.commit();

        console.log(`🔄 Tokens refreshed for session ${sid}`);

        return {
            accessToken: newAccessToken,
            refreshToken: newRefreshToken,
            accessTTL: ACCESS_TTL_SECONDS,
            refreshTTL: REFRESH_TTL_SECONDS
        };
    } catch (error) {
        await conn.rollback();
        throw error;
    } finally {
        conn.release();
    }
}

/**
 * Revoke session (logout)
 * @param {string} refreshToken - Refresh token to revoke
 * @param {string} reason - Reason for revocation
 * @returns {Promise<boolean>}
 */
async function revokeSession(refreshToken, reason = 'USER_LOGOUT') {
    try {
        const payload = jwt.verify(refreshToken, JWT_REFRESH_SECRET);
        const { sid } = payload;

        const [result] = await pool.execute(
            `UPDATE user_sessions SET revoked = 1, revoked_at = NOW(), revoked_reason = ? WHERE id = ? AND revoked = 0`,
            [reason, sid]
        );

        if (result.affectedRows > 0) {
            console.log(`🔒 Session revoked: ${sid}, reason: ${reason}`);
            return true;
        }
        return false;
    } catch (error) {
        console.error('❌ Error revoking session:', error);
        return false;
    }
}

/**
 * Revoke all sessions for a user
 * @param {string} userId
 * @param {string} reason
 * @returns {Promise<number>} - Number of sessions revoked
 */
async function revokeAllUserSessions(userId, reason = 'PASSWORD_RESET') {
    try {
        const [result] = await pool.execute(
            `UPDATE user_sessions SET revoked = 1, revoked_at = NOW(), revoked_reason = ?
             WHERE user_id = ? AND revoked = 0`,
            [reason, userId]
        );

        console.log(`🔒 Revoked ${result.affectedRows} sessions for user ${userId}, reason: ${reason}`);
        return result.affectedRows;
    } catch (error) {
        console.error('❌ Error revoking all user sessions:', error);
        return 0;
    }
}

/**
 * Get active sessions for a user
 * @param {string} userId
 * @returns {Promise<Array>}
 */
async function getUserSessions(userId) {
    try {
        const [sessions] = await pool.execute(
            `SELECT id, device_id, device_name, issued_at, last_seen_at, ip_address,
                    user_agent, refresh_expires_at
             FROM user_sessions
             WHERE user_id = ? AND revoked = 0 AND refresh_expires_at > NOW()
             ORDER BY last_seen_at DESC`,
            [userId]
        );
        return sessions;
    } catch (error) {
        console.error('❌ Error fetching user sessions:', error);
        return [];
    }
}

/**
 * Revoke specific session by ID
 * @param {string} sessionId
 * @param {string} userId - For authorization check
 * @returns {Promise<boolean>}
 */
async function revokeSessionById(sessionId, userId) {
    try {
        const [result] = await pool.execute(
            `UPDATE user_sessions SET revoked = 1, revoked_at = NOW(), revoked_reason = 'USER_REVOKED'
             WHERE id = ? AND user_id = ? AND revoked = 0`,
            [sessionId, userId]
        );
        return result.affectedRows > 0;
    } catch (error) {
        console.error('❌ Error revoking session by ID:', error);
        return false;
    }
}

/**
 * Cleanup expired sessions (run periodically)
 */
async function cleanupExpiredSessions() {
    try {
        const [result] = await pool.execute(
            `DELETE FROM user_sessions WHERE refresh_expires_at < DATE_SUB(NOW(), INTERVAL 7 DAY)`
        );
        console.log(`🧹 Cleaned up ${result.affectedRows} expired sessions`);
        return result.affectedRows;
    } catch (error) {
        console.error('❌ Error cleaning up sessions:', error);
        return 0;
    }
}

module.exports = {
    issueTokens,
    refreshTokens,
    revokeSession,
    revokeAllUserSessions,
    getUserSessions,
    revokeSessionById,
    cleanupExpiredSessions,
    ACCESS_TTL_SECONDS,
    REFRESH_TTL_SECONDS
};

