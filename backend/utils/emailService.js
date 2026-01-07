const nodemailer = require('nodemailer');
require('dotenv').config();

/**
 * ✅ Mask email for logging (security best practice)
 */
function maskEmail(email) {
    if (!email || typeof email !== 'string') return '***';
    const parts = email.split('@');
    if (parts.length !== 2) return '***';
    const localPart = parts[0];
    const domain = parts[1];
    const masked = localPart.length > 2
        ? localPart.substring(0, 2) + '***'
        : '***';
    return `${masked}@${domain}`;
}

// Cấu hình SMTP Gmail
const transporter = nodemailer.createTransport({
    service: 'gmail',
    auth: {
        user: process.env.SMTP_EMAIL,
        pass: process.env.SMTP_PASSWORD // App password từ Gmail
    }
});

// Hàm gửi OTP qua email
async function sendOTPEmail(email, otpCode, displayName = 'User') {
    try {
        const mailOptions = {
            from: process.env.SMTP_EMAIL,
            to: email,
            subject: '🔐 Mã OTP xác thực đăng ký tài khoản MOPR',
            html: `
                <!DOCTYPE html>
                <html>
                <head>
                    <meta charset="UTF-8">
                    <style>
                        body { font-family: Arial, sans-serif; background-color: #f5f5f5; }
                        .container { max-width: 600px; margin: 20px auto; background-color: white; padding: 30px; border-radius: 8px; box-shadow: 0 2px 4px rgba(0,0,0,0.1); }
                        .header { text-align: center; margin-bottom: 30px; }
                        .logo { font-size: 24px; font-weight: bold; color: #007bff; }
                        .content { text-align: center; }
                        .otp-code {
                            font-size: 32px;
                            font-weight: bold;
                            color: #007bff;
                            margin: 20px 0;
                            letter-spacing: 5px;
                            background-color: #f0f0f0;
                            padding: 15px;
                            border-radius: 5px;
                        }
                        .warning { color: #d9534f; font-size: 14px; margin-top: 20px; }
                        .footer { text-align: center; color: #666; font-size: 12px; margin-top: 30px; border-top: 1px solid #ddd; padding-top: 15px; }
                    </style>
                </head>
                <body>
                    <div class="container">
                        <div class="header">
                            <div class="logo">MOPR</div>
                        </div>

                        <div class="content">
                            <p>Xin chào <strong>${displayName}</strong>,</p>

                            <p>Bạn đã yêu cầu xác thực email để đăng ký tài khoản trên MOPR.</p>

                            <p>Mã OTP của bạn là:</p>
                            <div class="otp-code">${otpCode}</div>

                            <p><strong>Mã OTP sẽ hết hạn sau 2 phút.</strong></p>

                            <div class="warning">
                                ⚠️ Nếu bạn không yêu cầu đăng ký, vui lòng bỏ qua email này.
                            </div>
                        </div>

                        <div class="footer">
                            <p>© 2025 MOPR. All rights reserved.</p>
                            <p>Đây là email tự động, vui lòng không reply.</p>
                        </div>
                    </div>
                </body>
                </html>
            `
        };

        const info = await transporter.sendMail(mailOptions);
        // ✅ SECURITY: Don't log OTP or full email
        console.log('✅ Email sent successfully to:', maskEmail(email));
        return {
            success: true,
            messageId: info.messageId
        };
    } catch (error) {
        // ✅ SECURITY: Don't log full error which may contain sensitive data
        console.error('❌ Error sending email to:', maskEmail(email), 'Error:', error.message);
        return {
            success: false,
            error: error.message
        };
    }
}

// Hàm gửi email reset password (dùng sau)
async function sendResetPasswordEmail(email, otpCode, displayName = 'User') {
    try {
        console.log('📧 Attempting to send reset password email to:', maskEmail(email));
        console.log('🔑 OTP Code:', otpCode);
        console.log('👤 Display Name:', displayName);
        console.log('📤 SMTP Config:', {
            service: 'gmail',
            user: process.env.SMTP_EMAIL ? maskEmail(process.env.SMTP_EMAIL) : 'NOT SET',
            passSet: !!process.env.SMTP_PASSWORD
        });

        if (!process.env.SMTP_EMAIL || !process.env.SMTP_PASSWORD) {
            console.error('❌ SMTP credentials not configured in .env file!');
            return {
                success: false,
                error: 'SMTP credentials not configured'
            };
        }

        const mailOptions = {
            from: process.env.SMTP_EMAIL,
            to: email,
            subject: '🔐 Mã OTP đặt lại mật khẩu MOPR',
            html: `
                <!DOCTYPE html>
                <html>
                <head>
                    <meta charset="UTF-8">
                    <style>
                        body { font-family: Arial, sans-serif; background-color: #f5f5f5; }
                        .container { max-width: 600px; margin: 20px auto; background-color: white; padding: 30px; border-radius: 8px; box-shadow: 0 2px 4px rgba(0,0,0,0.1); }
                        .header { text-align: center; margin-bottom: 30px; }
                        .logo { font-size: 24px; font-weight: bold; color: #007bff; }
                        .content { text-align: center; }
                        .otp-code {
                            font-size: 32px;
                            font-weight: bold;
                            color: #007bff;
                            margin: 20px 0;
                            letter-spacing: 5px;
                            background-color: #f0f0f0;
                            padding: 15px;
                            border-radius: 5px;
                        }
                        .warning { color: #d9534f; font-size: 14px; margin-top: 20px; }
                        .footer { text-align: center; color: #666; font-size: 12px; margin-top: 30px; border-top: 1px solid #ddd; padding-top: 15px; }
                    </style>
                </head>
                <body>
                    <div class="container">
                        <div class="header">
                            <div class="logo">MOPR</div>
                        </div>

                        <div class="content">
                            <p>Xin chào <strong>${displayName}</strong>,</p>

                            <p>Bạn đã yêu cầu đặt lại mật khẩu cho tài khoản MOPR của mình.</p>

                            <p>Mã OTP của bạn là:</p>
                            <div class="otp-code">${otpCode}</div>

                            <p><strong>Mã OTP sẽ hết hạn sau 10 phút.</strong></p>

                            <div class="warning">
                                ⚠️ Nếu bạn không yêu cầu đặt lại mật khẩu, vui lòng bỏ qua email này hoặc liên hệ hỗ trợ ngay.
                            </div>
                        </div>

                        <div class="footer">
                            <p>© 2025 MOPR. All rights reserved.</p>
                            <p>Đây là email tự động, vui lòng không reply.</p>
                        </div>
                    </div>
                </body>
                </html>
            `
        };

        console.log('📨 Sending email via nodemailer...');
        const info = await transporter.sendMail(mailOptions);
        console.log('✅ Reset password email sent successfully to:', maskEmail(email));
        console.log('📬 Message ID:', info.messageId);
        return {
            success: true,
            messageId: info.messageId
        };
    } catch (error) {
        console.error('❌ Error sending reset password email to:', maskEmail(email));
        console.error('❌ Error details:', error.message);
        console.error('❌ Error code:', error.code);
        console.error('❌ Full error:', error);
        return {
            success: false,
            error: error.message
        };
    }
}

/**
 * ✅ Send password reset link via email
 * @param {string} email - User email
 * @param {string} resetLink - Password reset link with token
 * @param {string} displayName - User display name
 */
async function sendPasswordResetEmail(email, resetLink, displayName = 'User') {
    try {
        const mailOptions = {
            from: process.env.SMTP_EMAIL,
            to: email,
            subject: '🔐 Reset Your MOPR Password',
            html: `
                <!DOCTYPE html>
                <html>
                <head>
                    <meta charset="UTF-8">
                    <style>
                        body { font-family: Arial, sans-serif; background-color: #f5f5f5; }
                        .container { max-width: 600px; margin: 20px auto; background-color: white; padding: 30px; border-radius: 8px; box-shadow: 0 2px 4px rgba(0,0,0,0.1); }
                        .header { text-align: center; margin-bottom: 30px; }
                        .logo { font-size: 24px; font-weight: bold; color: #38B9FA; }
                        .content { line-height: 1.6; }
                        .reset-button {
                            display: inline-block;
                            background-color: #38B9FA;
                            color: white;
                            padding: 12px 30px;
                            text-decoration: none;
                            border-radius: 5px;
                            margin: 20px 0;
                            font-weight: bold;
                        }
                        .link-box {
                            background-color: #f0f0f0;
                            padding: 15px;
                            border-radius: 5px;
                            margin: 20px 0;
                            word-break: break-all;
                            font-size: 12px;
                            color: #666;
                        }
                        .warning {
                            color: #e05353;
                            font-size: 14px;
                            margin-top: 20px;
                            background-color: #FFEBEE;
                            padding: 15px;
                            border-radius: 5px;
                            border-left: 4px solid #e05353;
                        }
                        .info {
                            background-color: #E7F1FF;
                            padding: 15px;
                            border-radius: 5px;
                            border-left: 4px solid #38B9FA;
                            margin: 20px 0;
                        }
                        .footer { text-align: center; color: #666; font-size: 12px; margin-top: 30px; border-top: 1px solid #ddd; padding-top: 15px; }
                    </style>
                </head>
                <body>
                    <div class="container">
                        <div class="header">
                            <div class="logo">MOPR</div>
                        </div>

                        <div class="content">
                            <p>Hi <strong>${displayName}</strong>,</p>

                            <p>You requested to reset your password for your MOPR account.</p>

                            <p>Click the button below to reset your password:</p>

                            <div style="text-align: center;">
                                <a href="${resetLink}" class="reset-button">Reset Password</a>
                            </div>

                            <p style="text-align: center; color: #666; font-size: 12px;">Or copy and paste this link in your browser:</p>
                            <div class="link-box">${resetLink}</div>

                            <div class="info">
                                <strong>⏰ Important:</strong> This link will expire in <strong>15 minutes</strong> for security reasons.
                            </div>

                            <div class="warning">
                                <strong>⚠️ Security Notice:</strong><br>
                                If you didn't request this password reset, please ignore this email. Your password will remain unchanged.
                                <br><br>
                                If you believe someone is trying to access your account, please contact our support team immediately.
                            </div>
                        </div>

                        <div class="footer">
                            <p>© 2025 MOPR. All rights reserved.</p>
                            <p>This is an automated email, please do not reply.</p>
                        </div>
                    </div>
                </body>
                </html>
            `
        };

        const info = await transporter.sendMail(mailOptions);
        console.log('✅ Password reset email sent successfully to:', maskEmail(email));
        return {
            success: true,
            messageId: info.messageId
        };
    } catch (error) {
        console.error('❌ Error sending password reset email to:', maskEmail(email), 'Error:', error.message);
        return {
            success: false,
            error: error.message
        };
    }
}

/**
 * ✅ Send password changed notification
 * @param {string} email - User email
 * @param {string} displayName - User display name
 * @param {string} timestamp - When the password was changed
 * @param {string} ipAddress - IP address of the request
 */
async function sendPasswordChangedEmail(email, displayName = 'User', timestamp, ipAddress) {
    try {
        const mailOptions = {
            from: process.env.SMTP_EMAIL,
            to: email,
            subject: '✅ Your MOPR Password Was Changed',
            html: `
                <!DOCTYPE html>
                <html>
                <head>
                    <meta charset="UTF-8">
                    <style>
                        body { font-family: Arial, sans-serif; background-color: #f5f5f5; }
                        .container { max-width: 600px; margin: 20px auto; background-color: white; padding: 30px; border-radius: 8px; box-shadow: 0 2px 4px rgba(0,0,0,0.1); }
                        .header { text-align: center; margin-bottom: 30px; }
                        .logo { font-size: 24px; font-weight: bold; color: #38B9FA; }
                        .content { line-height: 1.6; }
                        .success-box {
                            background-color: #E8F5E9;
                            padding: 20px;
                            border-radius: 5px;
                            border-left: 4px solid #28A745;
                            margin: 20px 0;
                            text-align: center;
                        }
                        .success-icon {
                            font-size: 48px;
                            color: #28A745;
                        }
                        .info-box {
                            background-color: #f9f9f9;
                            padding: 15px;
                            border-radius: 5px;
                            margin: 20px 0;
                        }
                        .info-row {
                            display: flex;
                            justify-content: space-between;
                            padding: 8px 0;
                            border-bottom: 1px solid #e0e0e0;
                        }
                        .info-row:last-child {
                            border-bottom: none;
                        }
                        .warning {
                            color: #e05353;
                            font-size: 14px;
                            margin-top: 20px;
                            background-color: #FFEBEE;
                            padding: 15px;
                            border-radius: 5px;
                            border-left: 4px solid #e05353;
                        }
                        .support-button {
                            display: inline-block;
                            background-color: #e05353;
                            color: white;
                            padding: 12px 30px;
                            text-decoration: none;
                            border-radius: 5px;
                            margin: 10px 0;
                            font-weight: bold;
                        }
                        .footer { text-align: center; color: #666; font-size: 12px; margin-top: 30px; border-top: 1px solid #ddd; padding-top: 15px; }
                    </style>
                </head>
                <body>
                    <div class="container">
                        <div class="header">
                            <div class="logo">MOPR</div>
                        </div>

                        <div class="content">
                            <p>Hi <strong>${displayName}</strong>,</p>

                            <div class="success-box">
                                <div class="success-icon">✅</div>
                                <h2 style="margin: 10px 0; color: #28A745;">Password Changed Successfully</h2>
                            </div>

                            <p>Your MOPR account password was successfully changed.</p>

                            <div class="info-box">
                                <div class="info-row">
                                    <span><strong>Changed at:</strong></span>
                                    <span>${timestamp}</span>
                                </div>
                                <div class="info-row">
                                    <span><strong>IP Address:</strong></span>
                                    <span>${ipAddress || 'Unknown'}</span>
                                </div>
                            </div>

                            <p><strong>🔒 Security Notice:</strong></p>
                            <ul>
                                <li>All active sessions have been logged out for security</li>
                                <li>You'll need to log in again with your new password</li>
                                <li>This change affects all devices where you're logged in</li>
                            </ul>

                            <div class="warning">
                                <strong>⚠️ Didn't make this change?</strong><br><br>
                                If you didn't change your password, your account may have been compromised. Please contact our support team immediately.
                                <div style="text-align: center; margin-top: 15px;">
                                    <a href="mailto:${process.env.SMTP_EMAIL}" class="support-button">Contact Support</a>
                                </div>
                            </div>
                        </div>

                        <div class="footer">
                            <p>© 2025 MOPR. All rights reserved.</p>
                            <p>This is an automated security notification, please do not reply.</p>
                        </div>
                    </div>
                </body>
                </html>
            `
        };

        const info = await transporter.sendMail(mailOptions);
        console.log('✅ Password changed notification sent to:', maskEmail(email));
        return {
            success: true,
            messageId: info.messageId
        };
    } catch (error) {
        console.error('❌ Error sending password changed notification to:', maskEmail(email), 'Error:', error.message);
        return {
            success: false,
            error: error.message
        };
    }
}

module.exports = {
    sendOTPEmail,
    sendResetPasswordEmail,
    sendPasswordResetEmail,
    sendPasswordChangedEmail,
    maskEmail
};
