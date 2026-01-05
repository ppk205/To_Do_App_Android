const jwt = require('jsonwebtoken');


const JWT_SECRET = process.env.JWT_SECRET;

if (!JWT_SECRET) {
    throw new Error('CRITICAL: JWT_SECRET must be defined in environment variables');
}

/**
 * Middleware to authenticate JWT access token
 */
function authenticateToken(req, res, next) {
    const authHeader = req.headers['authorization'];
    const token = authHeader && authHeader.split(' ')[1]; // Bearer TOKEN

    if (!token) {
        return res.status(401).json({
            success: false,
            message: 'Access token required',
            code: 'TOKEN_MISSING'
        });
    }

    try {
        const payload = jwt.verify(token, JWT_SECRET);

        // Attach user info to request
        req.user = {
            id: payload.sub,
            email: payload.email,
            username: payload.username,
            sessionId: payload.sid,
            jti: payload.jti
        };

        next();
    } catch (error) {
        if (error.name === 'TokenExpiredError') {
            return res.status(401).json({
                success: false,
                message: 'Access token expired',
                code: 'TOKEN_EXPIRED'
            });
        }

        if (error.name === 'JsonWebTokenError') {
            return res.status(401).json({
                success: false,
                message: 'Invalid access token',
                code: 'TOKEN_INVALID'
            });
        }

        console.error('Token verification error:', error);
        return res.status(500).json({
            success: false,
            message: 'Internal server error'
        });
    }
}

/**
 * Optional middleware to check if token is blacklisted
 * (if implementing immediate token revocation)
 */
async function checkTokenBlacklist(req, res, next) {
    // TODO: Implement Redis blacklist check if needed
    // const jti = req.user?.jti;
    // if (jti && await isTokenBlacklisted(jti)) {
    //     return res.status(401).json({ success: false, message: 'Token revoked', code: 'TOKEN_REVOKED' });
    // }
    next();
}

module.exports = {
    authenticateToken,
    checkTokenBlacklist
};

