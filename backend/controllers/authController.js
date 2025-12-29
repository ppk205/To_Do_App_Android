const bcrypt = require('bcryptjs');
const jwt = require('jsonwebtoken');
const User = require('../models/User');
const RedisOTPService = require('../services/redisOTPService');
const tokenService = require('../services/tokenService');
const passwordResetService = require('../services/passwordResetService');
const { generateUserId, sanitizeUser } = require('../utils/helpers');
const { sendOTPEmail, sendResetPasswordEmail, sendPasswordResetEmail, sendPasswordChangedEmail } = require('../utils/emailService');

// ============================================
// 1. REGISTER (Step 1) - Lưu pending registration vào Redis + gửi OTP
// ============================================
async function register(req, res) {
    try {
        const { username, password, displayName, email, phone } = req.body;

        // Kiểm tra email đã tồn tại và verified
        const existingEmailUser = await User.findByEmail(email);
        if (existingEmailUser && existingEmailUser.verified === 1) {
            return res.status(400).json({
                success: false,
                message: 'Email đã được sử dụng'
            });
        }

        // Kiểm tra username đã tồn tại
        const existingUsernameUser = await User.findByUsername(username);
        if (existingUsernameUser && (!existingEmailUser || existingUsernameUser.id !== existingEmailUser.id)) {
            return res.status(400).json({
                success: false,
                message: 'Username đã tồn tại'
            });
        }

        // Hash password
        const hashedPassword = await bcrypt.hash(password, 10);

        // Chuẩn bị pending data (chưa lưu vào DB)
        const pendingData = {
            id: generateUserId(),
            username,
            hashedPassword,
            displayName,
            email,
            phone: phone || null,
            createdAt: Date.now()
        };

        // Sinh OTP và lưu pendingData vào Redis (KHÔNG tạo user DB ở bước này)
        const otpData = await RedisOTPService.createOTP(email, 'REGISTER', 2, pendingData); // 2 phút TTL

        if (!otpData.success) {
            return res.status(429).json({
                success: false,
                message: otpData.message || 'Không thể gửi OTP'
            });
        }

        // ✅ CRITICAL: Wait for email to be sent (Option 1: Synchronous)
        // This ensures user only gets success response if email was actually sent
        const emailResult = await sendOTPEmail(email, otpData.otpCode, displayName);

        if (!emailResult.success) {
            // Rollback: Clear OTP from Redis since email failed
            await RedisOTPService.invalidateOTP(email, 'REGISTER');
            return res.status(500).json({
                success: false,
                message: 'Không thể gửi email. Vui lòng thử lại sau.'
            });
        }

        // Trả response khi email đã được gửi thành công
        const responsePayload = {
            success: true,
            message: 'OTP đã được gửi đến email của bạn',
            email: email,
            userId: pendingData.id,
            expiresIn: (otpData.ttl || 2) * 60, // seconds
            resendAvailableIn: RedisOTPService.config.RESEND_COOLDOWN_SECONDS
        };

        res.status(201).json(responsePayload);


    } catch (error) {
        console.error('Register error:', error);
        res.status(500).json({
            success: false,
            message: 'Lỗi server khi đăng ký',
            error: error.message
        });
    }
}

// ============================================
// 2. VERIFY OTP (Step 3) - Xác thực OTP + Create user + Issue tokens
// ============================================
async function verifyOTP(req, res) {
    try {
        let { userId, email, otp, purpose } = req.body;

        purpose = (purpose || 'REGISTER').toUpperCase();

        // Nếu userId không được gửi, tìm pending registration trong Redis
        let pending = null;
        if (email) {
            pending = await RedisOTPService.getPendingRegistration(email);
        }

        // Nếu không có pending và userId không đưa lên, kiểm tra DB
        let user = null;
        if (userId) {
            user = await User.findById(userId);
        } else if (!pending) {
            user = await User.findByEmail(email);
            if (user) userId = user.id;
        }

        // Nếu không có pending và không có user -> not found
        if (!pending && !user) {
            return res.status(404).json({
                success: false,
                message: 'Không tìm thấy tài khoản hoặc đăng ký chưa được bắt đầu'
            });
        }

        // Nếu user exists in DB and already verified, reject
        if (user && user.verified === 1) {
            return res.status(400).json({ success: false, message: 'Tài khoản đã được xác thực' });
        }

        // Xác thực OTP từ Redis
        const otpResult = await RedisOTPService.verifyOTP(email, otp, purpose);

        if (!otpResult.success) {
            // Trả message cụ thể từ service
            const statusCode = otpResult.reason === 'FAIL_COOLDOWN' || otpResult.reason === 'MAX_ATTEMPTS_EXCEEDED' ? 423 : 400;
            return res.status(statusCode).json({
                success: false,
                message: otpResult.message,
                remainingAttempts: otpResult.remainingAttempts || 0,
                remainingSeconds: otpResult.remainingSeconds || 0,
                reason: otpResult.reason
            });
        }

        // OTP chính xác - tạo user nếu chưa có
        let createdUser = user;
        if (!createdUser) {
            // Pending phải tồn tại
            if (!pending) {
                return res.status(500).json({ success: false, message: 'Dữ liệu đăng ký không tồn tại' });
            }

            // Tạo user trong DB
            const userData = {
                id: pending.id,
                username: pending.username,
                hashedPassword: pending.hashedPassword,
                displayName: pending.displayName,
                email: pending.email,
                phone: pending.phone
            };

            await User.create(userData);
            await User.updateVerified(pending.id);
            createdUser = await User.findById(pending.id);

            // Xóa pending registration
            await RedisOTPService.clearPendingRegistration(email);
        } else {
            // Nếu user tồn tại nhưng chưa verified -> set verified
            await User.updateVerified(createdUser.id);
            createdUser = await User.findById(createdUser.id);
        }

        // Issue tokens
        const tokens = await tokenService.issueTokens(createdUser);

        res.status(200).json({
            success: true,
            message: 'Xác thực OTP thành công. Tài khoản đã được kích hoạt.',
            accessToken: tokens.accessToken,
            refreshToken: tokens.refreshToken,
            accessTTL: tokens.accessTTL,
            refreshTTL: tokens.refreshTTL,
            user: sanitizeUser(createdUser)
        });

    } catch (error) {
        console.error('Verify OTP error:', error);
        res.status(500).json({
            success: false,
            message: 'Lỗi server khi xác thực OTP',
            error: error.message
        });
    }
}

// ============================================
// 3. RESEND OTP - Gửi lại OTP
// ============================================
async function resendOTP(req, res) {
    try {
        let { userId, email, purpose } = req.body;
        purpose = (purpose || 'REGISTER').toUpperCase();

        // Nếu userId không được gửi, tìm pending registration hoặc user
        let pending = null;
        if (email) pending = await RedisOTPService.getPendingRegistration(email);

        let user = null;
        if (userId) {
            user = await User.findById(userId);
        } else if (!pending) {
            user = await User.findByEmail(email);
            if (user) userId = user.id;
        }

        if (!pending && !user) {
            return res.status(404).json({ success: false, message: 'Không tìm thấy tài khoản hoặc đăng ký chưa được bắt đầu' });
        }

        // Nếu user đã verified thì không resend
        if (user && user.verified === 1) {
            return res.status(400).json({ success: false, message: 'Tài khoản đã được xác thực' });
        }

        // Resend OTP (hàm sẽ kiểm tra fail/resend cooldown và trả về remainingSeconds nếu bị khóa)
        const pendingData = pending || (user ? { id: user.id, username: user.username, hashedPassword: user.hashedPassword, displayName: user.displayName, email: user.email, phone: user.phone } : null);
        const otpData = await RedisOTPService.resendOTP(email, purpose, 2, pendingData);

        if (!otpData.success) {
            const statusCode = otpData.reason === 'FAIL_COOLDOWN' ? 423 : 429;
            return res.status(statusCode).json({ success: false, message: otpData.message || 'Không thể gửi lại OTP', remainingSeconds: otpData.remainingSeconds || 0 });
        }

        // Gửi email
        const emailResult = await sendOTPEmail(email, otpData.otpCode, (pending && pending.displayName) || (user && user.displayName) || 'User');
        if (!emailResult.success) {
            return res.status(500).json({ success: false, message: 'Không thể gửi OTP qua email. Vui lòng thử lại.' });
        }

        // Trả về expiresIn để client biết thời gian TTL mới
        res.status(200).json({
            success: true,
            message: 'OTP mới đã được gửi đến email của bạn',
            expiresIn: (otpData.ttl || 2) * 60,
            resendAvailableIn: RedisOTPService.config.RESEND_COOLDOWN_SECONDS
        });

    } catch (error) {
        console.error('Resend OTP error:', error);
        res.status(500).json({ success: false, message: 'Lỗi server khi gửi lại OTP', error: error.message });
    }
}

// ============================================
// 4. LOGIN - Kiểm tra verified status và tạo session
// ============================================
async function login(req, res) {
    try {
        const { usernameOrEmail, password, deviceId, deviceName } = req.body;

        // Tìm user theo username hoặc email
        const user = await User.findByUsernameOrEmail(usernameOrEmail);

        if (!user) {
            return res.status(401).json({
                success: false,
                message: 'Username/Email hoặc mật khẩu không đúng'
            });
        }

        // ✅ Kiểm tra user đã verified
        if (!user.verified) {
            return res.status(403).json({
                success: false,
                message: 'Tài khoản chưa được xác thực. Vui lòng kiểm tra email và nhập OTP.',
                userId: user.id
            });
        }

        // Kiểm tra password
        const isPasswordValid = await bcrypt.compare(password, user.hashedPassword);

        if (!isPasswordValid) {
            return res.status(401).json({
                success: false,
                message: 'Username/Email hoặc mật khẩu không đúng'
            });
        }

        // Tạo session và issue tokens (OWASP MASTG compliant)
        const deviceInfo = {
            deviceId: deviceId || null,
            deviceName: deviceName || req.headers['user-agent'] || 'Unknown Device',
            ipAddress: req.ip || req.connection.remoteAddress,
            userAgent: req.headers['user-agent']
        };

        const tokens = await tokenService.issueTokens(user, deviceInfo);

        res.status(200).json({
            success: true,
            message: 'Đăng nhập thành công',
            accessToken: tokens.accessToken,
            refreshToken: tokens.refreshToken,
            accessTTL: tokens.accessTTL,
            refreshTTL: tokens.refreshTTL,
            sessionId: tokens.sessionId,
            user: sanitizeUser(user)
        });

    } catch (error) {
        console.error('Login error:', error);
        res.status(500).json({
            success: false,
            message: 'Lỗi server khi đăng nhập',
            error: error.message
        });
    }
}

// ============================================
// 5. REFRESH TOKEN - Rotate tokens and maintain session
// ============================================
async function refreshToken(req, res) {
    try {
        const { refreshToken } = req.body;

        if (!refreshToken) {
            return res.status(400).json({
                success: false,
                message: 'Refresh token required'
            });
        }

        const deviceInfo = {
            ipAddress: req.ip || req.connection.remoteAddress,
            userAgent: req.headers['user-agent']
        };

        const tokens = await tokenService.refreshTokens(refreshToken, deviceInfo);

        res.status(200).json({
            success: true,
            message: 'Tokens refreshed successfully',
            accessToken: tokens.accessToken,
            refreshToken: tokens.refreshToken,
            accessTTL: tokens.accessTTL,
            refreshTTL: tokens.refreshTTL
        });

    } catch (error) {
        console.error('Refresh token error:', error);

        // Map error codes to appropriate responses
        const errorMap = {
            'INVALID_TOKEN': { status: 401, message: 'Invalid refresh token', code: 'INVALID_TOKEN' },
            'SESSION_NOT_FOUND': { status: 401, message: 'Session not found', code: 'SESSION_EXPIRED' },
            'SESSION_REVOKED': { status: 401, message: 'Session has been revoked', code: 'SESSION_REVOKED' },
            'SESSION_EXPIRED': { status: 401, message: 'Session expired. Please login again', code: 'SESSION_EXPIRED' },
            'TOKEN_REUSE_DETECTED': { status: 401, message: 'Security violation detected. Please login again', code: 'TOKEN_REUSE' }
        };

        const errorInfo = errorMap[error.message] || { status: 500, message: 'Internal server error', code: 'SERVER_ERROR' };

        res.status(errorInfo.status).json({
            success: false,
            message: errorInfo.message,
            code: errorInfo.code
        });
    }
}

// ============================================
// 6. LOGOUT - Revoke session
// ============================================
async function logout(req, res) {
    try {
        const { refreshToken } = req.body;

        if (!refreshToken) {
            return res.status(400).json({
                success: false,
                message: 'Refresh token required'
            });
        }

        const revoked = await tokenService.revokeSession(refreshToken, 'USER_LOGOUT');

        if (revoked) {
            res.status(200).json({
                success: true,
                message: 'Đăng xuất thành công'
            });
        } else {
            res.status(404).json({
                success: false,
                message: 'Session not found or already revoked'
            });
        }

    } catch (error) {
        console.error('Logout error:', error);
        res.status(500).json({
            success: false,
            message: 'Lỗi server khi đăng xuất',
            error: error.message
        });
    }
}

// ============================================
// 7. GET USER SESSIONS - Device management
// ============================================
async function getUserSessions(req, res) {
    try {
        const userId = req.user.id; // From auth middleware

        const sessions = await tokenService.getUserSessions(userId);

        res.status(200).json({
            success: true,
            sessions: sessions
        });

    } catch (error) {
        console.error('Get sessions error:', error);
        res.status(500).json({
            success: false,
            message: 'Lỗi server khi lấy danh sách phiên',
            error: error.message
        });
    }
}

// ============================================
// 8. REVOKE SESSION - Revoke specific device/session
// ============================================
async function revokeSessionById(req, res) {
    try {
        const { sessionId } = req.body;
        const userId = req.user.id;

        if (!sessionId) {
            return res.status(400).json({
                success: false,
                message: 'Session ID required'
            });
        }

        const revoked = await tokenService.revokeSessionById(sessionId, userId);

        if (revoked) {
            res.status(200).json({
                success: true,
                message: 'Session revoked successfully'
            });
        } else {
            res.status(404).json({
                success: false,
                message: 'Session not found or unauthorized'
            });
        }

    } catch (error) {
        console.error('Revoke session error:', error);
        res.status(500).json({
            success: false,
            message: 'Lỗi server khi thu hồi phiên',
            error: error.message
        });
    }
}

// DEBUG: Get OTP status (TTL, attempts, cooldowns)
async function getOTPStatus(req, res) {
    try {
        const { email, purpose } = req.body;
        if (!email) {
            return res.status(400).json({ success: false, message: 'Missing email' });
        }

        const status = await RedisOTPService.getOTPStatus(email, (purpose || 'REGISTER').toUpperCase());
        if (!status) {
            return res.status(500).json({ success: false, message: 'Không thể lấy trạng thái OTP' });
        }

        return res.status(200).json({ success: true, status });
    } catch (error) {
        console.error('GetOTPStatus error:', error);
        res.status(500).json({ success: false, message: 'Lỗi server', error: error.message });
    }
}

// ============================================
// FORGOT PASSWORD FLOW (OTP-BASED)
// ============================================

/**
 * ✅ POST /api/auth/forgot-password
 * Request password reset OTP
 */
async function forgotPassword(req, res) {
    try {
        const { email } = req.body;

        console.log('🔍 Forgot password request received:', {
            email: email ? `${email.substring(0, 3)}***` : 'missing',
            ip: req.ip,
            hasAuthHeader: !!req.headers.authorization
        });

        // Get request info for security logging
        const requestInfo = {
            ipAddress: req.ip || req.connection?.remoteAddress,
            userAgent: req.headers['user-agent']
        };

        // ⚠️ SECURITY: Use setTimeout to prevent timing attacks
        const startTime = Date.now();

        // Generate reset OTP
        const result = await passwordResetService.generateResetOTP(email, requestInfo);

        // Send email if user exists (don't await to prevent timing attack)
        if (result.otp) {
            console.log('📧 Calling sendResetPasswordEmail for:', result.email ? result.email.substring(0, 3) + '***' : 'unknown');
            // Send OTP email asynchronously (fire and forget)
            sendResetPasswordEmail(result.email, result.otp, result.displayName)
                .then((emailResult) => {
                    if (emailResult && emailResult.success) {
                        console.log('✅ Email sent successfully, messageId:', emailResult.messageId);
                    } else {
                        console.error('❌ Email sending failed:', emailResult ? emailResult.error : 'Unknown error');
                    }
                })
                .catch(err => {
                    console.error('❌ Error in sendResetPasswordEmail promise:', err.message);
                    console.error('❌ Stack:', err.stack);
                });
        } else {
            console.log('⚠️ No OTP generated, email will not be sent');
        }

        // ✅ SECURITY: Always add artificial delay to prevent timing attacks
        const elapsedTime = Date.now() - startTime;
        const minResponseTime = 500; // 500ms minimum response time

        if (elapsedTime < minResponseTime) {
            await new Promise(resolve => setTimeout(resolve, minResponseTime - elapsedTime));
        }

        // ✅ SECURITY: Always return same success message (prevent user enumeration)
        res.status(200).json({
            success: true,
            message: 'If the email exists, an OTP has been sent. Please check your inbox.',
            note: 'The OTP will expire in 10 minutes.'
        });

    } catch (error) {
        console.error('Forgot password error:', error);
        // ❌ SECURITY: Don't expose error details
        res.status(500).json({
            success: false,
            message: 'An error occurred. Please try again later.'
        });
    }
}

/**
 * ✅ POST /api/auth/verify-reset-otp
 * Verify password reset OTP
 */
async function verifyResetOTP(req, res) {
    try {
        const { email, otp } = req.body;

        if (!email || !otp) {
            return res.status(400).json({
                success: false,
                message: 'Email and OTP are required'
            });
        }

        const verification = await passwordResetService.verifyResetOTP(email, otp);

        if (!verification.valid) {
            return res.status(400).json({
                success: false,
                message: verification.message,
                attemptsLeft: verification.attemptsLeft
            });
        }

        // ✅ Return minimal information
        res.status(200).json({
            success: true,
            message: 'OTP verified successfully',
            email: email // Return email for next step
        });

    } catch (error) {
        console.error('Verify reset OTP error:', error);
        res.status(500).json({
            success: false,
            message: 'An error occurred while verifying OTP'
        });
    }
}

/**
 * ✅ POST /api/auth/resend-reset-otp
 * Resend password reset OTP
 */
async function resendResetOTP(req, res) {
    try {
        const { email } = req.body;

        if (!email) {
            return res.status(400).json({
                success: false,
                message: 'Email is required'
            });
        }

        const result = await passwordResetService.resendResetOTP(email);

        if (!result.success) {
            return res.status(429).json({
                success: false,
                message: result.message,
                cooldownRemaining: result.cooldownRemaining
            });
        }

        // Send email if user exists
        if (result.otp) {
            console.log('📧 Resending OTP email for:', result.email ? result.email.substring(0, 3) + '***' : 'unknown');
            sendResetPasswordEmail(result.email, result.otp, result.displayName)
                .then((emailResult) => {
                    if (emailResult && emailResult.success) {
                        console.log('✅ Resend email sent successfully, messageId:', emailResult.messageId);
                    } else {
                        console.error('❌ Resend email failed:', emailResult ? emailResult.error : 'Unknown error');
                    }
                })
                .catch(err => {
                    console.error('❌ Error in resendResetPasswordEmail promise:', err.message);
                });
        }

        res.status(200).json({
            success: true,
            message: 'If the email exists, a new OTP has been sent'
        });

    } catch (error) {
        console.error('Resend reset OTP error:', error);
        res.status(500).json({
            success: false,
            message: 'An error occurred while resending OTP'
        });
    }
}

/**
 * ✅ POST /api/auth/reset-password
 * Reset password with verified OTP
 */
async function resetPassword(req, res) {
    try {
        const { email, otp, newPassword } = req.body;

        if (!email || !otp || !newPassword) {
            return res.status(400).json({
                success: false,
                message: 'Email, OTP, and new password are required'
            });
        }

        // Get user for validation
        const user = await User.findByEmail(email);

        if (!user) {
            return res.status(404).json({
                success: false,
                message: 'User not found'
            });
        }

        // ✅ SECURITY: Check if new password is different from old password
        const isSamePassword = await bcrypt.compare(newPassword, user.hashedPassword);
        if (isSamePassword) {
            return res.status(400).json({
                success: false,
                message: 'New password must be different from the old password'
            });
        }

        // Hash new password
        const newPasswordHash = await bcrypt.hash(newPassword, 10);

        // Reset password (verifies OTP inside)
        const result = await passwordResetService.resetPassword(email, otp, newPasswordHash);

        if (!result.success) {
            return res.status(400).json({
                success: false,
                message: result.message
            });
        }

        // ✅ SECURITY: Send email notification (async, don't wait)
        const timestamp = new Date().toLocaleString('en-US', {
            timeZone: 'Asia/Ho_Chi_Minh',
            year: 'numeric',
            month: 'long',
            day: 'numeric',
            hour: '2-digit',
            minute: '2-digit'
        });

        const ipAddress = req.ip || req.connection?.remoteAddress;

        sendPasswordChangedEmail(user.email, user.displayName, timestamp, ipAddress)
            .catch(err => console.error('Error sending password changed notification:', err.message));

        res.status(200).json({
            success: true,
            message: 'Password reset successfully. All sessions have been logged out. Please login with your new password.',
            note: 'A confirmation email has been sent to your email address.'
        });

    } catch (error) {
        console.error('Reset password error:', error);
        res.status(500).json({
            success: false,
            message: 'An error occurred while resetting password'
        });
    }
}

module.exports = {
    register,
    verifyOTP,
    resendOTP,
    login,
    refreshToken,
    logout,
    getUserSessions,
    revokeSessionById,
    getOTPStatus,
    forgotPassword,
    verifyResetOTP,
    resendResetOTP,
    resetPassword
};
