const redisClient = require('../config/redis');
const { ensureRedisConnected } = require('../config/redis');
const crypto = require('crypto');

/**
 * ========================================
 * REDIS OTP SERVICE - FULLY FIXED
 * ========================================
 *
 * Flow:
 * 1. Gửi OTP: TTL 2 phút, Resend cooldown 60s
 * 2. Nhập sai ≥5 lần:
 *    - OTP bị hủy
 *    - Xóa resend cooldown (tránh cooldown kép)
 *    - Set fail cooldown 60s
 * 3. Sau 60s: Cho resend OTP mới với attempts reset
 *
 * FIX:
 * - Xóa resend cooldown khi verify thành công
 * - Kiểm tra OTP có tồn tại trước khi check resend cooldown
 */

class RedisOTPService {
    static config = {
        OTP_LENGTH: 6,
        OTP_TTL_MINUTES: 2,
        RESEND_COOLDOWN_SECONDS: 30,
        MAX_ATTEMPTS: 5,
        FAIL_COOLDOWN_SECONDS: 60,
        HMAC_SECRET: process.env.OTP_HMAC_SECRET || 'your-secret-key-here'
    };

    static generateOTP() {
        return crypto.randomInt(100000, 1000000).toString().padStart(this.config.OTP_LENGTH, '0');
    }

    static hashOTP(otpCode) {
        return crypto
            .createHmac('sha256', this.config.HMAC_SECRET)
            .update(otpCode)
            .digest('hex');
    }

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
     */
    static async createOTP(email, purpose = 'REGISTER', ttlMinutes = null, pendingData = null) {
        try {
            ensureRedisConnected();
            ttlMinutes = ttlMinutes || this.config.OTP_TTL_MINUTES;

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

            // 2. 🔧 FIX: Kiểm tra OTP có tồn tại không
            const otpKey = this.getOTPKey(email, purpose);
            const otpExists = await redisClient.exists(otpKey);

            // 3. Chỉ check resend cooldown nếu OTP còn tồn tại
            const resendCooldownKey = this.getResendCooldownKey(email, purpose);

            if (otpExists) {
                // OTP còn tồn tại → check resend cooldown
                const resendTTL = await redisClient.ttl(resendCooldownKey);
                if (resendTTL > 0) {
                    return {
                        success: false,
                        message: `Vui lòng đợi ${resendTTL} giây trước khi gửi lại OTP`,
                        remainingSeconds: resendTTL,
                        reason: 'RESEND_COOLDOWN'
                    };
                }
            } else {
                // 🔧 FIX: OTP không tồn tại (hết hạn/đã xóa) → xóa resend cooldown cũ
                await redisClient.del(resendCooldownKey);
            }

            // 4. Sinh OTP
            const otpCode = this.generateOTP();
            const otpHash = this.hashOTP(otpCode);

            // 5. Lưu vào Redis
            const attemptsKey = this.getAttemptsKey(email, purpose);
            const ttlSeconds = ttlMinutes * 60;

            const pipeline = redisClient.multi();
            pipeline.setEx(otpKey, ttlSeconds, otpHash);
            pipeline.setEx(attemptsKey, ttlSeconds, '0');
            pipeline.setEx(resendCooldownKey, this.config.RESEND_COOLDOWN_SECONDS, '1');

            if (pendingData) {
                const pendingKey = this.getPendingRegKey(email);
                const pendingTTL = ttlSeconds + 300;
                pipeline.setEx(pendingKey, pendingTTL, JSON.stringify(pendingData));
            }

            await pipeline.exec();

            const expiresAt = new Date(Date.now() + ttlMinutes * 60 * 1000);
            console.log(`✅ OTP created for ${email} (${purpose}), expires: ${expiresAt.toISOString()}`);

            return {
                success: true,
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
     */
    static async verifyOTP(email, otpCode, purpose = 'REGISTER', deleteOnVerify = true) {
        try {
            ensureRedisConnected();
            const otpKey = this.getOTPKey(email, purpose);
            const attemptsKey = this.getAttemptsKey(email, purpose);
            const failCooldownKey = this.getFailCooldownKey(email, purpose);

            // 1. Kiểm tra fail cooldown
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

            // 3. Tăng attempts
            const newAttempts = await redisClient.incr(attemptsKey);

            // Restore TTL nếu bị mất
            const attemptsTTL = await redisClient.ttl(attemptsKey);
            if (attemptsTTL < 0) {
                const otpTTL = await redisClient.ttl(otpKey);
                if (otpTTL > 0) await redisClient.expire(attemptsKey, otpTTL);
            }

            if (newAttempts > this.config.MAX_ATTEMPTS) {
                await this.invalidateOTPWithCooldown(email, purpose);
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
                isValid = false;
            }

            if (!isValid) {
                const remainingAttempts = this.config.MAX_ATTEMPTS - newAttempts;

                if (remainingAttempts === 0) {
                    await this.invalidateOTPWithCooldown(email, purpose);
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

            // 5. OTP đúng → Lấy pendingData
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

            if (deleteOnVerify) {
                // REGISTER flow: Xóa tất cả
                await this.invalidateOTP(email, purpose);
                if (pendingKey) {
                    await redisClient.del(pendingKey);
                }
                console.log(`✅ OTP verified and deleted for ${email} (${purpose})`);
            } else {
                // 🔧 FIX: RESET_PASSWORD flow: Reset attempts + XÓA resend cooldown
                const pipeline = redisClient.multi();
                pipeline.set(attemptsKey, '0');
                pipeline.del(this.getResendCooldownKey(email, purpose));
                await pipeline.exec();

                // Restore TTL cho attempts
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
     * 🛡️ Xóa OTP + Set fail cooldown (dùng khi nhập sai ≥5 lần)
     */
    static async invalidateOTPWithCooldown(email, purpose) {
        try {
            ensureRedisConnected();
            const pipeline = redisClient.multi();
            pipeline.del(this.getOTPKey(email, purpose));
            pipeline.del(this.getAttemptsKey(email, purpose));
            pipeline.del(this.getResendCooldownKey(email, purpose));
            pipeline.setEx(this.getFailCooldownKey(email, purpose), this.config.FAIL_COOLDOWN_SECONDS, '1');
            await pipeline.exec();

            console.log(`🛡️ OTP invalidated with fail cooldown for ${email} (${purpose})`);
        } catch (error) {
            console.error('❌ Error invalidating OTP with cooldown:', error);
        }
    }

    /**
     * 🔄 Resend OTP
     */
    static async resendOTP(email, purpose = 'REGISTER', ttlMinutes = null, pendingData = null) {
        try {
            ensureRedisConnected();

            // Kiểm tra fail cooldown
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

            // Tạo OTP mới
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

    static async clearPendingRegistration(email) {
        try {
            ensureRedisConnected();
            const pendingKey = this.getPendingRegKey(email);
            await redisClient.del(pendingKey);
        } catch (error) {
            console.error('❌ Error clearing pending registration:', error);
        }
    }

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
