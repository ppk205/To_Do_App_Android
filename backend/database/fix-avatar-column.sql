-- Migration: Expand avatarUrl column to support file paths
-- Run this on your Azure MySQL database

USE mopr;

-- Change avatarUrl from VARCHAR(255) to TEXT (supports paths up to 65KB)
ALTER TABLE User MODIFY COLUMN avatarUrl TEXT;

-- Verify the change
SHOW COLUMNS FROM User LIKE 'avatarUrl';

