const { body, validationResult } = require('express-validator');

// Validation middleware cho register
const registerValidation = [
    body('username')
        .trim()
        .isLength({ min: 3, max: 100 })
        .withMessage('Username phải có từ 3-100 ký tự')
        .matches(/^[a-zA-Z0-9_]+$/)
        .withMessage('Username chỉ được chứa chữ cái, số và dấu gạch dưới'),

    body('password')
        .isLength({ min: 6 })
        .withMessage('Mật khẩu phải có ít nhất 6 ký tự'),

    body('displayName')
        .trim()
        .notEmpty()
        .withMessage('Display name không được để trống')
        .isLength({ max: 150 })
        .withMessage('Display name không được vượt quá 150 ký tự'),

    body('email')
        .trim()
        .isEmail()
        .withMessage('Email không hợp lệ')
        .normalizeEmail(),

    body('phone')
        .optional({ nullable: true, checkFalsy: true })
        .trim()
        .matches(/^[0-9+\-\s()]+$/)
        .withMessage('Số điện thoại không hợp lệ')
];

// Validation middleware cho login
const loginValidation = [
    body('usernameOrEmail')
        .trim()
        .notEmpty()
        .withMessage('Username hoặc email không được để trống'),

    body('password')
        .notEmpty()
        .withMessage('Mật khẩu không được để trống')
];

// Middleware kiểm tra validation errors
const validate = (req, res, next) => {
    const errors = validationResult(req);
    if (!errors.isEmpty()) {
        return res.status(400).json({
            success: false,
            message: errors.array()[0].msg,
            errors: errors.array()
        });
    }
    next();
};

module.exports = {
    registerValidation,
    loginValidation,
    validate
};

