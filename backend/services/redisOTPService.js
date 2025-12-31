const redisClient = require('../config/redis');
const { ensureRedisConnected } = require('../config/redis');
const crypto = require('crypto');

/**
 * ========================================
 * REDIS OTP SERVICE - ADJUSTED TIMING
 * ========================================
 *
 * Flow:
 * 1. Gửi OTP: TTL 2 phút, Resend cooldown 60s
 * 2. Nhập sai ≥5 lần:
 *    - OTP bị hủy
 *    - Cooldown 60s (không verify, không resend)
 * 3. Sau 60s: Cho resend OTP mới với attempts reset
 */

class RedisOTPService {
    // ⏱️ Cấu hình thời gian
    static config = {
        OTP_LENGTH: 6,
        OTP_TTL_MINUTES: 2,              // TTL OTP: 2 phút
        RESEND_COOLDOWN_SECONDS: 60,     // Cooldown giữa các lần gửi: 60s
        MAX_ATTEMPTS: 5,                 // Số lần nhập tối đa: 5
        FAIL_COOLDOWN_SECONDS: 60,       // Cooldown sau khi nhập sai ≥5: 60s
        HMAC_SECRET: process.env.OTP_HMAC_SECRET || 'your-secret-key-here'
    };

    /**
     * Sinh OTP 6 chữ số
     */
    static generateOTP() {
        // crypto.randomInt is exclusive on upper bound, use 1000000 to include 999999
        return crypto.randomInt(100000, 1000000).toString().padStart(this.config.OTP_LENGTH, '0');
    }

    /**
     * Tạo HMAC cho OTP
     */
    static hashOTP(otpCode) {
        return crypto
            .createHmac('sha256', this.config.HMAC_SECRET)
            .update(otpCode)
            .digest('hex');
    }

    /**
     * Redis keys
     */
    static getOTPKey(email, purpose) {
        return `otp:${email.toLowerCase()}:${purpose}`;
    }

    static getAttemptsKey(email, purpose) {
        return `otp_attempts:${email.toLowerCase()}:${purpose}`;
    }

    static getFailCooldownKey(email, purpose) {
        return `otp_fail_cooldown:${email.toLowerCase()}:${purpose}`;
    }

    static getResendCooldownKey(email, purpose) {
        return `otp_resend_cooldown:${email.toLowerCase()}:${purpose}`;
    }

    static getPendingRegKey(email) {
        return `pending_reg:${email.toLowerCase()}`;
    }

    /**
     * ✅ Tạo OTP với rate limiting
     * pendingData: optional object sẽ được lưu tạm trong Redis để tạo user sau khi verify
     */
    static async createOTP(email, purpose = 'REGISTER', ttlMinutes = null, pendingData = null) {
        try {
            ensureRedisConnected();
             ttlMinutes = ttlMinutes || this.config.OTP_TTL_MINUTES;

            // 1. Kiểm tra fail cooldown (sau khi nhập sai ≥5 lần)
            const failCooldownKey = this.getFailCooldownKey(email, purpose);
            const failCooldownTTL = await redisClient.ttl(failCooldownKey);

            if (failCooldownTTL > 0) {
                return {
                    success: false,
                    message: `Bạn đã nhập sai quá nhiều. Vui lòng đợi ${failCooldownTTL} giây`,
                    remainingSeconds: failCooldownTTL,
                    reason: 'FAIL_COOLDOWN'
                };
            }

            // 2. Kiểm tra resend cooldown (60s giữa các lần gửi)
            const resendCooldownKey = this.getResendCooldownKey(email, purpose);
            const resendTTL = await redisClient.ttl(resendCooldownKey);

            if (resendTTL > 0) {
                return {
                    success: false,
                    message: `Vui lòng đợi ${resendTTL} giây trước khi gửi lại OTP`,
                    remainingSeconds: resendTTL,
                    reason: 'RESEND_COOLDOWN'
                };
            }

            // 3. Sinh OTP
            const otpCode = this.generateOTP();
            const otpHash = this.hashOTP(otpCode);

            // 4. Lưu vào Redis (dùng pipeline)
            const otpKey = this.getOTPKey(email, purpose);
            const attemptsKey = this.getAttemptsKey(email, purpose);
            const ttlSeconds = ttlMinutes * 60;

            const pipeline = redisClient.multi();
            pipeline.setEx(otpKey, ttlSeconds, otpHash);
            pipeline.setEx(attemptsKey, ttlSeconds, '0');
            pipeline.setEx(resendCooldownKey, this.config.RESEND_COOLDOWN_SECONDS, '1');

            // Nếu có pendingData, lưu để dùng khi verify thành công
            if (pendingData) {
                const pendingKey = this.getPendingRegKey(email);
                // Lưu TTL dài hơn OTP một chút (OTP TTL + 5 phút) để tránh mất data khi user chờ
                const pendingTTL = ttlSeconds + 300;
                pipeline.setEx(pendingKey, pendingTTL, JSON.stringify(pendingData));
            }

            await pipeline.exec();

            const expiresAt = new Date(Date.now() + ttlMinutes * 60 * 1000);

            console.log(`✅ OTP created for ${email} (${purpose}), expires: ${expiresAt.toISOString()}`);

            return {
                success: true,
                // Lưu ý: otpCode chỉ nên trả trong môi trường dev; production không trả
                otpCode,
                expiresAt,
                ttl: ttlMinutes
            };
        } catch (error) {
            console.error('❌ Error creating OTP:', error);
            return {
                success: false,
                message: 'Hệ thống đang bảo trì, vui lòng thử lại sau'
            };
        }
    }

    /**
     * ✅ Xác thực OTP
     * @param {boolean} deleteOnVerify - If true, delete OTP after successful verification (default: true)
     *                                   For RESET_PASSWORD, should be false (only delete after password change)
     */
    static async verifyOTP(email, otpCode, purpose = 'REGISTER', deleteOnVerify = true) {
        try {
            ensureRedisConnected();
             const otpKey = this.getOTPKey(email, purpose);
            const attemptsKey = this.getAttemptsKey(email, purpose);
            const failCooldownKey = this.getFailCooldownKey(email, purpose);

            // 1. Kiểm tra fail cooldown (đang trong 60s sau khi nhập sai ≥5)
            const failCooldownTTL = await redisClient.ttl(failCooldownKey);
            if (failCooldownTTL > 0) {
                return {
                    success: false,
                    message: `Bạn đã nhập sai quá nhiều. Vui lòng đợi ${failCooldownTTL} giây để gửi lại OTP`,
                    remainingSeconds: failCooldownTTL,
                    reason: 'FAIL_COOLDOWN',
                    canResend: false
                };
            }

            // 2. Lấy OTP hash
            const otpHash = await redisClient.get(otpKey);
            if (!otpHash) {
                return {
                    success: false,
                    message: 'OTP hết hạn hoặc không tồn tại',
                    reason: 'OTP_NOT_FOUND',
                    canResend: true
                };
            }

            // 3. Kiểm tra và tăng attempts (atomic via INCR)
            // Note: attemptsKey should already exist with TTL set in createOTP
            const newAttempts = await redisClient.incr(attemptsKey);

            // Restore TTL on attemptsKey if it was missing TTL (edge case)
            const attemptsTTL = await redisClient.ttl(attemptsKey);
            if (attemptsTTL < 0) {
                // set expire to OTP TTL
                const otpTTL = await redisClient.ttl(otpKey);
                if (otpTTL > 0) await redisClient.expire(attemptsKey, otpTTL);
            }

            if (newAttempts > this.config.MAX_ATTEMPTS) {
                // Đã vượt quá max attempts → Hủy OTP và set cooldown
                await this.invalidateOTP(email, purpose);
                await redisClient.setEx(failCooldownKey, this.config.FAIL_COOLDOWN_SECONDS, '1');

                return {
                    success: false,
                    message: 'Bạn đã nhập sai quá nhiều. Vui lòng đợi 60 giây để gửi lại OTP',
                    remainingSeconds: this.config.FAIL_COOLDOWN_SECONDS,
                    reason: 'MAX_ATTEMPTS_EXCEEDED',
                    canResend: false
                };
            }

            // 4. So sánh OTP
            const inputHash = this.hashOTP(otpCode);

            let isValid = false;
            try {
                isValid = crypto.timingSafeEqual(
                    Buffer.from(otpHash),
                    Buffer.from(inputHash)
                );
            } catch (e) {
                // timingSafeEqual throws if buffers lengths differ; fallback to false
                isValid = false;
            }

            if (!isValid) {
                const remainingAttempts = this.config.MAX_ATTEMPTS - newAttempts;

                // Nếu đây là lần thử cuối cùng
                if (remainingAttempts === 0) {
                    await this.invalidateOTP(email, purpose);
                    await redisClient.setEx(failCooldownKey, this.config.FAIL_COOLDOWN_SECONDS, '1');

                    return {
                        success: false,
                        message: 'Bạn đã nhập sai 5 lần. Vui lòng đợi 60 giây để gửi lại OTP',
                        remainingAttempts: 0,
                        remainingSeconds: this.config.FAIL_COOLDOWN_SECONDS,
                        reason: 'MAX_ATTEMPTS_REACHED',
                        canResend: false
                    };
                }

                return {
                    success: false,
                    message: `OTP không chính xác. Còn ${remainingAttempts} lần thử`,
                    remainingAttempts,
                    reason: 'INVALID_OTP'
                };
            }

            // 5. OTP đúng → Lấy pendingData trước khi xóa
            let pendingData = null;
            const pendingKey = this.getPendingRegKey(email);
            const pendingDataStr = await redisClient.get(pendingKey);

            if (pendingDataStr) {
                try {
                    pendingData = JSON.parse(pendingDataStr);
                } catch (e) {
                    console.error('❌ Error parsing pendingData:', e);
                }
            }

            // Chỉ xóa OTP nếu deleteOnVerify = true (REGISTER flow)
            // RESET_PASSWORD flow: giữ OTP để verify lần nữa khi reset password
            if (deleteOnVerify) {
                // Xóa OTP và attempts
                await this.invalidateOTP(email, purpose);

                // Xóa pendingData
                if (pendingKey) {
                    await redisClient.del(pendingKey);
                }
                console.log(`✅ OTP verified and deleted for ${email} (${purpose})`);
            } else {
                // Chỉ reset attempts về 0 để cho phép verify lại
                await redisClient.set(attemptsKey, '0');
                const otpTTL = await redisClient.ttl(otpKey);
                if (otpTTL > 0) {
                    await redisClient.expire(attemptsKey, otpTTL);
                }
                console.log(`✅ OTP verified but kept for ${email} (${purpose})`);
            }

            return {
                success: true,
                message: 'OTP xác thực thành công',
                pendingData: pendingData
            };

        } catch (error) {
            console.error('❌ Error verifying OTP:', error);
            return {
                success: false,
                message: 'Hệ thống đang bảo trì, vui lòng thử lại sau'
            };
        }
    }

    /**
     * 🗑️ Xóa OTP và attempts (không xóa fail_cooldown)
     */
    static async invalidateOTP(email, purpose) {
        try {
            ensureRedisConnected();
             const pipeline = redisClient.multi();
            pipeline.del(this.getOTPKey(email, purpose));
            pipeline.del(this.getAttemptsKey(email, purpose));
            pipeline.del(this.getResendCooldownKey(email, purpose));
            await pipeline.exec();

            console.log(`🗑️ OTP invalidated for ${email} (${purpose})`);
        } catch (error) {
            console.error('❌ Error invalidating OTP:', error);
        }
    }

    /**
     * 🔄 Resend OTP
     * - Trong 60s fail cooldown: ❌ không được resend
     * - Trong 60s resend cooldown: ❌ không được resend
     * - Sau 60s: ✅ cho resend với attempts reset
     */
    static async resendOTP(email, purpose = 'REGISTER', ttlMinutes = null, pendingData = null) {
        try {
            ensureRedisConnected();
             // 1. Kiểm tra fail cooldown
            const failCooldownKey = this.getFailCooldownKey(email, purpose);
            const failCooldownTTL = await redisClient.ttl(failCooldownKey);

            if (failCooldownTTL > 0) {
                return {
                    success: false,
                    message: `Bạn đã nhập sai quá nhiều. Vui lòng đợi ${failCooldownTTL} giây`,
                    remainingSeconds: failCooldownTTL,
                    reason: 'FAIL_COOLDOWN'
                };
            }

            // 2. Tạo OTP mới (hàm createOTP đã check resend cooldown)
            const result = await this.createOTP(email, purpose, ttlMinutes, pendingData);

            if (result.success) {
                console.log(`🔄 OTP resent for ${email} (${purpose})`);
            }

            return result;

        } catch (error) {
            console.error('❌ Error resending OTP:', error);
            return {
                success: false,
                message: 'Hệ thống đang bảo trì, vui lòng thử lại sau'
            };
        }
    }

    /**
     * ℹ️ Lấy trạng thái OTP hiện tại
     */
    static async getOTPStatus(email, purpose) {
        try {
            ensureRedisConnected();
             const otpKey = this.getOTPKey(email, purpose);
            const attemptsKey = this.getAttemptsKey(email, purpose);
            const failCooldownKey = this.getFailCooldownKey(email, purpose);
            const resendCooldownKey = this.getResendCooldownKey(email, purpose);

            const [otpExists, otpTTL, attempts, failCooldownTTL, resendCooldownTTL] = await Promise.all([
                redisClient.exists(otpKey),
                redisClient.ttl(otpKey),
                redisClient.get(attemptsKey),
                redisClient.ttl(failCooldownKey),
                redisClient.ttl(resendCooldownKey)
            ]);

            const currentAttempts = parseInt(attempts || '0');
            const remainingAttempts = this.config.MAX_ATTEMPTS - currentAttempts;

            return {
                otpExists: otpExists === 1,
                otpTTL: otpTTL > 0 ? otpTTL : 0,
                currentAttempts,
                remainingAttempts: remainingAttempts > 0 ? remainingAttempts : 0,
                inFailCooldown: failCooldownTTL > 0,
                failCooldownTTL: failCooldownTTL > 0 ? failCooldownTTL : 0,
                inResendCooldown: resendCooldownTTL > 0,
                resendCooldownTTL: resendCooldownTTL > 0 ? resendCooldownTTL : 0,
                canVerify: otpExists === 1 && failCooldownTTL <= 0,
                canResend: failCooldownTTL <= 0 && resendCooldownTTL <= 0
            };
        } catch (error) {
            console.error('❌ Error getting OTP status:', error);
            return null;
        }
    }

    /**
     * Lấy pending registration (nếu có)
     */
    static async getPendingRegistration(email) {
        try {
            ensureRedisConnected();
             const pendingKey = this.getPendingRegKey(email);
            const raw = await redisClient.get(pendingKey);
            if (!raw) return null;
            try {
                return JSON.parse(raw);
            } catch (e) {
                return null;
            }
        } catch (error) {
            console.error('❌ Error getting pending registration:', error);
            return null;
        }
    }

    /**
     * Xóa pending registration
     */
    static async clearPendingRegistration(email) {
        try {
            ensureRedisConnected();
             const pendingKey = this.getPendingRegKey(email);
            await redisClient.del(pendingKey);
        } catch (error) {
            console.error('❌ Error clearing pending registration:', error);
        }
    }

    /**
     * 🧹 Xóa tất cả (bao gồm cả fail_cooldown) - dùng khi cần reset hoàn toàn
     */
    static async clearAll(email, purpose) {
        try {
            ensureRedisConnected();
             const pipeline = redisClient.multi();
            pipeline.del(this.getOTPKey(email, purpose));
            pipeline.del(this.getAttemptsKey(email, purpose));
            pipeline.del(this.getFailCooldownKey(email, purpose));
            pipeline.del(this.getResendCooldownKey(email, purpose));
            pipeline.del(this.getPendingRegKey(email));
            await pipeline.exec();

            console.log(`🧹 All OTP data cleared for ${email} (${purpose})`);
        } catch (error) {
            console.error('❌ Error clearing OTP data:', error);
        }
    }
}

module.exports = RedisOTPService;

