-- Migration: Thêm các cột social media links vào bảng users

ALTER TABLE users
ADD COLUMN IF NOT EXISTS githubUrl VARCHAR(255) DEFAULT NULL,
ADD COLUMN IF NOT EXISTS linkedinUrl VARCHAR(255) DEFAULT NULL,
ADD COLUMN IF NOT EXISTS websiteUrl VARCHAR(255) DEFAULT NULL;

-- Kiểm tra kết quả
DESCRIBE users;

