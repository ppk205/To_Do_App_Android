-- Migration: thêm cột verified cho bảng users và tạo bảng otps cho lưu mã OTP
-- Chạy trên MySQL 8+ (hỗ trợ ADD COLUMN IF NOT EXISTS)

START TRANSACTION;

-- Thêm cột verified vào users nếu chưa tồn tại
ALTER TABLE `users`
  ADD COLUMN IF NOT EXISTS `verified` TINYINT(1) NOT NULL DEFAULT 0;

COMMIT;

-- Tạo bảng otps để lưu mã OTP (hash), trạng thái, thời hạn
CREATE TABLE IF NOT EXISTS `otps` (
  `id` VARCHAR(100) NOT NULL PRIMARY KEY,
  `userId` VARCHAR(100) NOT NULL,
  `email` VARCHAR(150) NOT NULL,
  `otpHash` VARCHAR(255) NOT NULL,
  `purpose` VARCHAR(50) NOT NULL,
  `attempts` INT NOT NULL DEFAULT 0,
  `used` TINYINT(1) NOT NULL DEFAULT 0,
  `expiresAt` DATETIME NOT NULL,
  `createdAt` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  INDEX `idx_otps_userId` (`userId`),
  INDEX `idx_otps_email` (`email`),
  INDEX `idx_otps_expiresAt` (`expiresAt`),
  CONSTRAINT `fk_otps_user` FOREIGN KEY (`userId`) REFERENCES `users`(`id`) ON DELETE CASCADE ON UPDATE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Ghi chú:
-- - `id` có thể là UUID (varchar(100)) hoặc dạng khác tuỳ implement.
-- - `otpHash` lưu mã OTP dưới dạng hash (ví dụ bcrypt) để không lưu mã thuần.
-- - `purpose` dùng để phân biệt OTP cho register / reset-password / email-change.
-- - Thời hạn OTP nên được đặt khi insert (ví dụ: NOW() + INTERVAL 10 MINUTE).
