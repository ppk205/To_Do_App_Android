/**
 * ========================================
 * CRON JOB: Cleanup Expired Password Reset Tokens
 * ========================================
 *
 * This script should be run periodically (e.g., every hour)
 * to clean up expired and used password reset tokens
 *
 * Usage:
 *   node cleanup-reset-tokens.js
 *
 * Schedule with cron (Linux/Mac):
 *   0 * * * * /usr/bin/node /path/to/cleanup-reset-tokens.js >> /var/log/token-cleanup.log 2>&1
 *
 * Schedule with Task Scheduler (Windows):
 *   Create a new task that runs: node cleanup-reset-tokens.js
 *   Set it to run every hour
 */

const passwordResetService = require('./services/passwordResetService');
require('dotenv').config();

async function cleanup() {
    console.log(`[${new Date().toISOString()}] Starting password reset token cleanup...`);

    try {
        const result = await passwordResetService.cleanupExpiredTokens();

        if (result.success) {
            console.log(`[${new Date().toISOString()}] ✅ Cleanup completed. Deleted ${result.deletedCount} tokens.`);
        } else {
            console.error(`[${new Date().toISOString()}] ❌ Cleanup failed.`);
        }

        process.exit(0);
    } catch (error) {
        console.error(`[${new Date().toISOString()}] ❌ Cleanup error:`, error.message);
        process.exit(1);
    }
}

// Run cleanup
cleanup();

