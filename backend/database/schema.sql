-- CREATE DATABASE IF NOT EXISTS mopr;
-- USE mopr;

-- Tạo bảng users
CREATE TABLE IF NOT EXISTS users (
    id VARCHAR(100) PRIMARY KEY,
    username VARCHAR(100) NOT NULL UNIQUE,
    hashedPassword VARCHAR(255) NOT NULL,
    displayName VARCHAR(150) NOT NULL,
    email VARCHAR(150) NOT NULL UNIQUE,
    phone VARCHAR(20),
    avatarUrl VARCHAR(255),
    avatarId VARCHAR(255),
    bio TEXT,
    verified TINYINT(1) DEFAULT 0,
    createdAt DATETIME DEFAULT CURRENT_TIMESTAMP,
    updatedAt DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,

    -- Indexes for better performance
    INDEX idx_username (username),
    INDEX idx_email (email),
    INDEX idx_created_at (createdAt)
);

-- Tạo bảng otps cho register và reset password
CREATE TABLE IF NOT EXISTS otps (
    id VARCHAR(100) PRIMARY KEY,
    userId VARCHAR(100) NOT NULL,
    email VARCHAR(150) NOT NULL,
    otpHash VARCHAR(255) NOT NULL,
    purpose VARCHAR(50) NOT NULL,
    attempts INT DEFAULT 0,
    used TINYINT(1) DEFAULT 0,
    expiresAt DATETIME NOT NULL,
    createdAt DATETIME DEFAULT CURRENT_TIMESTAMP,

    FOREIGN KEY (userId) REFERENCES users(id) ON DELETE CASCADE,
    INDEX idx_user_purpose (userId, purpose),
    INDEX idx_expires_at (expiresAt),
    INDEX idx_email (email)
);

-- Sample data for testing (optional)
-- INSERT INTO users (id, username, hashedPassword, displayName, email, phone)
-- VALUES ('test-user-001', 'testuser', '$2a$10$rN.xYpKpWVKHBxLwQqz1De8d8YcL1eLlKgZHN6yY0XVcH3P8ZwYlW', 'Test User', 'test@example.com', '0123456789');

