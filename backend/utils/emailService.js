const nodemailer = require('nodemailer');
require('dotenv').config();

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

                            <p><strong>Mã OTP sẽ hết hạn sau 5 phút.</strong></p>

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
        console.log('✅ Email sent successfully:', info.messageId);
        return {
            success: true,
            messageId: info.messageId
        };
    } catch (error) {
        console.error('❌ Error sending email:', error.message);
        return {
            success: false,
            error: error.message
        };
    }
}

// Hàm gửi email reset password (dùng sau)
async function sendResetPasswordEmail(email, otpCode, displayName = 'User') {
    try {
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

        const info = await transporter.sendMail(mailOptions);
        console.log('✅ Reset password email sent successfully:', info.messageId);
        return {
            success: true,
            messageId: info.messageId
        };
    } catch (error) {
        console.error('❌ Error sending reset password email:', error.message);
        return {
            success: false,
            error: error.message
        };
    }
}

module.exports = {
    sendOTPEmail,
    sendResetPasswordEmail
};

