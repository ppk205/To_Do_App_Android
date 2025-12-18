const bcrypt = require('bcryptjs');
const jwt = require('jsonwebtoken');
const User = require('../models/User');
const OTP = require('../models/OTP');
const { generateUserId, sanitizeUser } = require('../utils/helpers');
const { sendOTPEmail } = require('../utils/emailService');

// ============================================
// 1. REGISTER (Step 1) - Tạo user chưa verify + gửi OTP
// ============================================
async function register(req, res) {
    try {
        const { username, password, displayName, email, phone } = req.body;

        // Kiểm tra username đã tồn tại
        const existingUsername = await User.isUsernameExists(username);
        if (existingUsername) {
            return res.status(400).json({
                success: false,
                message: 'Username đã tồn tại'
            });
        }

        // Kiểm tra email đã tồn tại
        const existingEmail = await User.isEmailExists(email);
        if (existingEmail) {
            return res.status(400).json({
                success: false,
                message: 'Email đã được sử dụng'
            });
        }

        // Hash password
        const hashedPassword = await bcrypt.hash(password, 10);

        // Tạo user ID
        const userId = generateUserId();

        // Tạo user mới (chưa verified)
        const userData = {
            id: userId,
            username,
            hashedPassword,
            displayName,
            email,
            phone: phone || null,
            verified: 0 // ❌ Không verify ngay, chờ OTP
        };

        await User.create(userData);

        // Sinh OTP
        const otpData = await OTP.create(userId, email, 'REGISTER');

        // Gửi OTP qua email
        const emailResult = await sendOTPEmail(email, otpData.otpCode, displayName);

        if (!emailResult.success) {
            return res.status(500).json({
                success: false,
                message: 'Không thể gửi OTP qua email. Vui lòng thử lại.'
            });
        }

        // ✅ CHỈ trả userId, KHÔNG trả token
        res.status(201).json({
            success: true,
            message: 'OTP đã được gửi đến email của bạn',
            userId: userId,
            email: email
        });

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
// 2. VERIFY OTP (Step 3) - Xác thực OTP + Active account
// ============================================
async function verifyOTP(req, res) {
    try {
        let { userId, email, otp, purpose } = req.body;

        purpose = (purpose || 'REGISTER').toUpperCase();

        // Nếu userId không được gửi, tìm theo email (case-insensitive)
        let user = null;
        if (userId) {
            user = await User.findById(userId);
        } else {
            user = await User.findByEmail(email);
            if (user) userId = user.id;
        }

        // Kiểm tra user tồn tại
        if (!user) {
            return res.status(404).json({
                success: false,
                message: 'Không tìm thấy tài khoản'
            });
        }

        // Kiểm tra email match
        if (user.email.toLowerCase() !== (email || '').toLowerCase()) {
            return res.status(400).json({
                success: false,
                message: 'Email không khớp'
            });
        }

        // Xác thực OTP
        const otpResult = await OTP.verifyOTP(userId, otp, purpose);

        if (!otpResult.success) {
            // Trả message cụ thể từ model
            return res.status(400).json({
                success: false,
                message: otpResult.message
            });
        }

        // ✅ OTP chính xác - Active user (set verified = 1)
        await User.updateVerified(userId);

        // Lấy user info sau khi verified
        const verifiedUser = await User.findById(userId);

        // Generate JWT token
        const token = jwt.sign(
            { userId: verifiedUser.id, username: verifiedUser.username, email: verifiedUser.email },
            process.env.JWT_SECRET,
            { expiresIn: '30d' }
        );

        res.status(200).json({
            success: true,
            message: 'Xác thực OTP thành công. Tài khoản đã được kích hoạt.',
            token: token,
            user: sanitizeUser(verifiedUser)
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

        // Nếu userId không được gửi, tìm theo email
        let user = null;
        if (userId) {
            user = await User.findById(userId);
        } else {
            user = await User.findByEmail(email);
            if (user) userId = user.id;
        }

        // Kiểm tra user tồn tại
        if (!user) {
            return res.status(404).json({
                success: false,
                message: 'Không tìm thấy tài khoản'
            });
        }

        // Kiểm tra email match
        if (user.email.toLowerCase() !== (email || '').toLowerCase()) {
            return res.status(400).json({
                success: false,
                message: 'Email không khớp'
            });
        }

        // Kiểm tra giới hạn resend: tối đa 3 lần trong 1 giờ
        const recentCount = await OTP.countRecentOTPs(userId, purpose, 60); // phút
        if (recentCount >= 3) {
            return res.status(429).json({
                success: false,
                message: 'Vượt quá số lần yêu cầu OTP. Vui lòng thử lại sau'
            });
        }

        // Invalidate OTP cũ
        await OTP.invalidateOldOTPs(userId, purpose);

        // Sinh OTP mới
        const otpData = await OTP.create(userId, email, purpose);

        // Gửi OTP qua email
        const emailResult = await sendOTPEmail(email, otpData.otpCode, user.displayName);

        if (!emailResult.success) {
            return res.status(500).json({
                success: false,
                message: 'Không thể gửi OTP qua email. Vui lòng thử lại.'
            });
        }

        res.status(200).json({
            success: true,
            message: 'OTP mới đã được gửi đến email của bạn'
        });

    } catch (error) {
        console.error('Resend OTP error:', error);
        res.status(500).json({
            success: false,
            message: 'Lỗi server khi gửi lại OTP',
            error: error.message
        });
    }
}

// ============================================
// 4. LOGIN - Kiểm tra verified status
// ============================================
async function login(req, res) {
    try {
        const { usernameOrEmail, password } = req.body;

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

        // Tạo JWT token
        const token = jwt.sign(
            { userId: user.id, username: user.username, email: user.email },
            process.env.JWT_SECRET,
            { expiresIn: '30d' }
        );

        res.status(200).json({
            success: true,
            message: 'Đăng nhập thành công',
            user: sanitizeUser(user),
            token: token
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

module.exports = {
    register,
    verifyOTP,
    resendOTP,
    login
};
