const { ensureRedisConnected } = require('../config/redis');
const redisClient = require('../config/redis');

/**
 * ========================================
 * RATE LIMITER MIDDLEWARE
 * ========================================
 *
 * Redis-based rate limiting to prevent brute force attacks
 * - Login attempts: 5 attempts per 15 minutes
 * - General API: configurable per endpoint
 */

/**
 * ✅ Rate limit middleware for login attempts
 * Prevents brute force attacks on authentication
 */
async function loginRateLimiter(req, res, next) {
    try {
        ensureRedisConnected();

        const identifier = req.body.usernameOrEmail || req.ip;
        const key = `login_attempts:${identifier.toLowerCase()}`;
        const maxAttempts = 5;
        const windowSeconds = 900; // 15 minutes

        const attempts = await redisClient.get(key);
        const currentAttempts = attempts ? parseInt(attempts) : 0;

        if (currentAttempts >= maxAttempts) {
            const ttl = await redisClient.ttl(key);
            return res.status(429).json({
                success: false,
                message: `Quá nhiều lần đăng nhập thất bại. Vui lòng thử lại sau ${Math.ceil(ttl / 60)} phút.`,
                code: 'TOO_MANY_ATTEMPTS',
                retryAfter: ttl
            });
        }

        // Store original send to increment counter after failed login
        const originalJson = res.json.bind(res);
        res.json = function(data) {
            // Only increment on failed login (401/403)
            if (res.statusCode === 401 || res.statusCode === 403) {
                redisClient.incr(key)
                    .then(() => redisClient.expire(key, windowSeconds))
                    .catch(err => console.error('Rate limiter increment error:', err));
            }
            // On successful login (200), clear the counter
            else if (res.statusCode === 200 && data.success) {
                redisClient.del(key)
                    .catch(err => console.error('Rate limiter clear error:', err));
            }
            return originalJson(data);
        };

        next();
    } catch (error) {
        console.error('Rate limiter error:', error);
        // Fail open - don't block requests if Redis is down
        next();
    }
}

/**
 * ✅ Generic rate limiter factory
 * @param {number} maxRequests - Maximum requests allowed
 * @param {number} windowSeconds - Time window in seconds
 * @param {string} keyPrefix - Redis key prefix
 */
function createRateLimiter(maxRequests = 10, windowSeconds = 60, keyPrefix = 'rate_limit') {
    return async (req, res, next) => {
        try {
            ensureRedisConnected();

            const identifier = req.user?.id || req.ip;
            const key = `${keyPrefix}:${identifier}`;

            const requests = await redisClient.incr(key);

            if (requests === 1) {
                await redisClient.expire(key, windowSeconds);
            }

            if (requests > maxRequests) {
                const ttl = await redisClient.ttl(key);
                return res.status(429).json({
                    success: false,
                    message: 'Quá nhiều yêu cầu. Vui lòng thử lại sau.',
                    code: 'RATE_LIMIT_EXCEEDED',
                    retryAfter: ttl
                });
            }

            // Add rate limit headers
            res.setHeader('X-RateLimit-Limit', maxRequests);
            res.setHeader('X-RateLimit-Remaining', Math.max(0, maxRequests - requests));
            res.setHeader('X-RateLimit-Reset', Date.now() + (windowSeconds * 1000));

            next();
        } catch (error) {
            console.error('Rate limiter error:', error);
            // Fail open - don't block requests if Redis is down
            next();
        }
    };
}

module.exports = {
    loginRateLimiter,
    createRateLimiter
};

