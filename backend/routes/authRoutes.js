const express = require('express');
const router = express.Router();
const authController = require('../controllers/authController');
const {
    registerValidation,
    loginValidation,
    verifyOTPValidation,
    resendOTPValidation,
    validate
} = require('../middleware/validation');

// Register route - Step 1: Create user + Send OTP
router.post('/register', registerValidation, validate, authController.register);

// Verify OTP route - Step 3: Verify OTP + Activate account
router.post('/verify-otp', verifyOTPValidation, validate, authController.verifyOTP);

// Resend OTP route - Resend OTP if expired or lost
router.post('/resend-otp', resendOTPValidation, validate, authController.resendOTP);

// Login route
router.post('/login', loginValidation, validate, authController.login);

module.exports = router;

