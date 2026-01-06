const { body, validationResult } = require('express-validator');
const sanitizeHtml = require('sanitize-html');

const sanitizeInput = (value) => {
    if (typeof value !== 'string') return value;
    return sanitizeHtml(value, {
        allowedTags: [], // Strip all HTML tags
        allowedAttributes: {}
    });
};

// Validation middleware cho register
const registerValidation = [
    body('username')
        .trim()
        .customSanitizer(sanitizeInput)
        .isLength({ min: 3, max: 100 })
        .withMessage('Username phải có từ 3-100 ký tự')
        .matches(/^[a-zA-Z0-9_]+$/)
        .withMessage('Username chỉ được chứa chữ cái, số và dấu gạch dưới'),

    body('password')
        .isLength({ min: 8 })
        .withMessage('Mật khẩu phải có ít nhất 8 ký tự')
        .matches(/^(?=.*[a-z])(?=.*[A-Z])(?=.*\d)(?=.*[!@#$%^&*])/)
        .withMessage('Mật khẩu phải có ít nhất 1 chữ hoa, 1 chữ thường, 1 số và 1 ký tự đặc biệt (!@#$%^&*)'),

    body('displayName')
        .trim()
        .customSanitizer(sanitizeInput)
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
        .customSanitizer(sanitizeInput)
        .matches(/^[0-9+\-\s()]+$/)
        .withMessage('Số điện thoại không hợp lệ')
];

// Validation middleware cho login
const loginValidation = [
    body('usernameOrEmail')
        .trim()
        .customSanitizer(sanitizeInput)
        .notEmpty()
        .withMessage('Username hoặc email không được để trống'),

    body('password')
        .notEmpty()
        .withMessage('Mật khẩu không được để trống')
];

// Validation middleware cho verify OTP
const verifyOTPValidation = [
    // userId có thể optional - controller sẽ dùng email để tìm nếu không có
    body('userId')
        .optional({ nullable: true })
        .trim(),

    body('email')
        .trim()
        .isEmail()
        .withMessage('Email không hợp lệ')
        .normalizeEmail(),

    body('otp')
        .trim()
        .isLength({ min: 6, max: 6 })
        .withMessage('OTP phải có 6 chữ số')
        .isNumeric()
        .withMessage('OTP phải là số'),

    // purpose optional - controller sẽ mặc định 'REGISTER'
    body('purpose')
        .optional({ nullable: true })
        .trim()
        .isIn(['REGISTER', 'RESET_PASSWORD'])
        .withMessage('Purpose không hợp lệ')
];

// Validation middleware cho resend OTP
const resendOTPValidation = [
    // userId optional
    body('userId')
        .optional({ nullable: true })
        .trim(),

    body('email')
        .trim()
        .isEmail()
        .withMessage('Email không hợp lệ')
        .normalizeEmail(),

    body('purpose')
        .optional({ nullable: true })
        .trim()
        .isIn(['REGISTER', 'RESET_PASSWORD'])
        .withMessage('Purpose không hợp lệ')
];

// Validation middleware cho register-init (chỉ email)
const registerInitValidation = [
    body('email')
        .trim()
        .isEmail()
        .withMessage('Email không hợp lệ')
        .normalizeEmail(),

    body('displayName')
        .optional({ nullable: true })
        .trim()
        .customSanitizer(sanitizeInput)
        .isLength({ max: 150 })
        .withMessage('Display name không được vượt quá 150 ký tự'),

    body('phone')
        .optional({ nullable: true })
        .trim()
        .customSanitizer(sanitizeInput)
        .matches(/^[0-9+\-\s()]+$/)
        .withMessage('Số điện thoại không hợp lệ')
];

/**
 * ✅ Validation for forgot password request
 */
const forgotPasswordValidation = [
    body('email')
        .trim()
        .isEmail()
        .withMessage('Email không hợp lệ')
        .normalizeEmail()
        .customSanitizer(sanitizeInput)
];

/**
 * ✅ Validation for reset password request (OTP-based)
 */
const resetPasswordValidation = [
    body('email')
        .trim()
        .isEmail()
        .withMessage('Email không hợp lệ')
        .normalizeEmail()
        .customSanitizer(sanitizeInput),

    body('otp')
        .trim()
        .isLength({ min: 6, max: 6 })
        .withMessage('OTP phải có 6 chữ số')
        .isNumeric()
        .withMessage('OTP phải là số'),

    body('newPassword')
        .isLength({ min: 8 })
        .withMessage('Mật khẩu phải có ít nhất 8 ký tự')
        .matches(/^(?=.*[a-z])(?=.*[A-Z])(?=.*\d)(?=.*[!@#$%^&*])/)
        .withMessage('Mật khẩu phải có ít nhất 1 chữ hoa, 1 chữ thường, 1 số và 1 ký tự đặc biệt (!@#$%^&*)'),

    body('confirmPassword')
        .custom((value, { req }) => {
            if (value !== req.body.newPassword) {
                throw new Error('Mật khẩu xác nhận không khớp');
            }
            return true;
        })
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
    verifyOTPValidation,
    resendOTPValidation,
    registerInitValidation,
    forgotPasswordValidation,
    resetPasswordValidation,
    validate
};
