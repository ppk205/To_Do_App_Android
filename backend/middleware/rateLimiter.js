const rateLimit = require('express-rate-limit');

/**
 * Rate limiter for login endpoint
 * Prevents brute force attacks on login
 */
const loginRateLimiter = rateLimit({
    windowMs: 15 * 60 * 1000, // 15 minutes
    max: 5, // Limit each IP to 5 requests per windowMs
    message: {
        success: false,
        message: 'Quá nhiều lần thử đăng nhập từ IP này. Vui lòng thử lại sau 15 phút.'
    },
    standardHeaders: true, // Return rate limit info in the `RateLimit-*` headers
    legacyHeaders: false, // Disable the `X-RateLimit-*` headers
    skipSuccessfulRequests: false, // Count successful requests
    skipFailedRequests: false // Count failed requests
});

/**
 * Generic rate limiter factory
 * Can be used to create custom rate limiters for different endpoints
 *
 * @param {number} windowMs - Time window in milliseconds
 * @param {number} max - Maximum number of requests per window
 * @param {string} message - Custom error message
 */
const createRateLimiter = (windowMs = 15 * 60 * 1000, max = 100, message = 'Quá nhiều yêu cầu. Vui lòng thử lại sau.') => {
    return rateLimit({
        windowMs,
        max,
        message: {
            success: false,
            message
        },
        standardHeaders: true,
        legacyHeaders: false
    });
};

/**
 * Rate limiter for registration endpoint
 * Prevents spam registration
 */
const registerRateLimiter = rateLimit({
    windowMs: 60 * 60 * 1000, // 1 hour
    max: 3, // Limit each IP to 3 registration attempts per hour
    message: {
        success: false,
        message: 'Quá nhiều lần thử đăng ký từ IP này. Vui lòng thử lại sau 1 giờ.'
    },
    standardHeaders: true,
    legacyHeaders: false,
    skipSuccessfulRequests: false
});

/**
 * Rate limiter for OTP endpoints (verify, resend)
 * Prevents OTP brute force and spam
 */
const otpRateLimiter = rateLimit({
    windowMs: 5 * 60 * 1000, // 5 minutes
    max: 10, // Limit each IP to 10 OTP requests per 5 minutes
    message: {
        success: false,
        message: 'Quá nhiều yêu cầu OTP. Vui lòng thử lại sau 5 phút.'
    },
    standardHeaders: true,
    legacyHeaders: false
});

/**
 * Rate limiter for password reset endpoints
 * Prevents password reset abuse
 */
const passwordResetRateLimiter = rateLimit({
    windowMs: 60 * 60 * 1000, // 1 hour
    max: 5, // Limit each IP to 5 password reset attempts per hour
    message: {
        success: false,
        message: 'Quá nhiều lần yêu cầu đặt lại mật khẩu. Vui lòng thử lại sau 1 giờ.'
    },
    standardHeaders: true,
    legacyHeaders: false
});

/**
 * General API rate limiter
 * Applies to all API endpoints
 */
const generalApiLimiter = rateLimit({
    windowMs: 15 * 60 * 1000, // 15 minutes
    max: 100, // Limit each IP to 100 requests per windowMs
    message: {
        success: false,
        message: 'Quá nhiều yêu cầu từ IP này. Vui lòng thử lại sau.'
    },
    standardHeaders: true,
    legacyHeaders: false
});

module.exports = {
    loginRateLimiter,
    createRateLimiter,
    registerRateLimiter,
    otpRateLimiter,
    passwordResetRateLimiter,
    generalApiLimiter
};

