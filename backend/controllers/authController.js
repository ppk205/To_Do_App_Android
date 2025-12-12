const bcrypt = require('bcryptjs');
const jwt = require('jsonwebtoken');
const User = require('../models/User');
const { generateUserId, sanitizeUser } = require('../utils/helpers');

// Register controller
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

        // Tạo user mới
        const userData = {
            id: userId,
            username,
            hashedPassword,
            displayName,
            email,
            phone: phone || null
        };

        await User.create(userData);

        // Lấy thông tin user vừa tạo
        const newUser = await User.findById(userId);

        // Tạo JWT token
        const token = jwt.sign(
            { userId: newUser.id, username: newUser.username },
            process.env.JWT_SECRET,
            { expiresIn: '30d' }
        );

        // Trả về response
        res.status(201).json({
            success: true,
            message: 'Đăng ký thành công',
            user: sanitizeUser(newUser),
            token
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

// Login controller
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
            { userId: user.id, username: user.username },
            process.env.JWT_SECRET,
            { expiresIn: '30d' }
        );

        // Trả về response
        res.status(200).json({
            success: true,
            message: 'Đăng nhập thành công',
            user: sanitizeUser(user),
            token
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
    login
};

