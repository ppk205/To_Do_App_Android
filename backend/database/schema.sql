-- Tạo database (nếu chưa có)
-- Password: password123
-- ('test-user-001', 'testuser', '$2a$10$rN.xYpKpWVKHBxLwQqz1De8d8YcL1eLlKgZHN6yY0XVcH3P8ZwYlW', 'Test User', 'test@example.com', '0123456789');
-- VALUES
-- INSERT INTO User (id, username, hashedPassword, displayName, email, phone)
-- Sample data for testing (optional)

);
    INDEX idx_created_at (createdAt)
    INDEX idx_email (email),
    INDEX idx_username (username),
    -- Indexes for better performance

    updatedAt DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    createdAt DATETIME DEFAULT CURRENT_TIMESTAMP,
    phone VARCHAR(20),
    bio TEXT,
    avatarId VARCHAR(255),
    avatarUrl VARCHAR(255),
    email VARCHAR(150) NOT NULL UNIQUE,
    displayName VARCHAR(150) NOT NULL,
    hashedPassword VARCHAR(255) NOT NULL,
    username VARCHAR(100) NOT NULL UNIQUE,
    id VARCHAR(100) PRIMARY KEY,
CREATE TABLE IF NOT EXISTS User (
-- Tạo bảng User

-- USE mopr;
-- CREATE DATABASE IF NOT EXISTS mopr;

