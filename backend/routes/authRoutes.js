const express = require('express');
const router = express.Router();
const authController = require('../controllers/authController');
const { authenticateToken } = require('../middleware/authMiddleware');
const { loginRateLimiter, createRateLimiter } = require('../middleware/rateLimiter');
const {
    registerValidation,
    loginValidation,
    verifyOTPValidation,
    resendOTPValidation,
    registerInitValidation,
    validate
} = require('../middleware/validation');

// ============================================
// PUBLIC ROUTES (No authentication required)
// ============================================

// Register route - Step 1: Create user + Send OTP
router.post('/register', registerValidation, validate, authController.register);

// Register-init (email only)
router.post('/register-init', registerInitValidation, validate, authController.register);

// Verify OTP route - Step 3: Verify OTP + Activate account
router.post('/verify-otp', verifyOTPValidation, validate, authController.verifyOTP);

// Resend OTP route - Resend OTP if expired or lost
router.post('/resend-otp', resendOTPValidation, validate, authController.resendOTP);

// Debug: Get OTP status (TTL, attempts, cooldowns)
router.post('/otp-status', authController.getOTPStatus);

// ✅ Login route with rate limiting - Issue access + refresh tokens
router.post('/login', loginRateLimiter, loginValidation, validate, authController.login);

// Refresh token route - Rotate tokens
router.post('/refresh', authController.refreshToken);

// Logout route - Revoke session
router.post('/logout', authController.logout);

// ============================================
// PROTECTED ROUTES (Authentication required)
// ============================================

// Get current user profile
router.get('/profile', authenticateToken, authController.getProfile);

// Get user's active sessions
router.get('/sessions', authenticateToken, authController.getUserSessions);

// Revoke specific session by ID
router.post('/sessions/revoke', authenticateToken, authController.revokeSessionById);

// Update user profile (with optional avatar upload)
router.put('/profile', authenticateToken, upload.single('avatar'), authController.updateProfile);

module.exports = router;
