/**
 * Migration script: Fix avatarUrl column size
 * Run: node backend/fix-avatar-column.js
 */

require('dotenv').config();
const mysql = require('mysql2/promise');

async function runMigration() {
    let connection;

    try {
        // Connect to database
        connection = await mysql.createConnection({
            host: process.env.DB_HOST,
            user: process.env.DB_USER,
            password: process.env.DB_PASSWORD,
            database: process.env.DB_NAME,
            ssl: {
                rejectUnauthorized: false
            }
        });

        console.log('✅ Connected to database');

        // Check current column type
        const [columns] = await connection.execute(
            "SHOW COLUMNS FROM users LIKE 'avatarUrl'"
        );

        if (columns.length > 0) {
            console.log('📋 Current avatarUrl column:', columns[0]);
        }

        // Run migration
        console.log('🔧 Altering avatarUrl column to TEXT...');
        await connection.execute('ALTER TABLE users MODIFY COLUMN avatarUrl TEXT');

        console.log('✅ Migration completed successfully!');

        // Verify
        const [newColumns] = await connection.execute(
            "SHOW COLUMNS FROM users LIKE 'avatarUrl'"
        );
        console.log('📋 New avatarUrl column:', newColumns[0]);

    } catch (error) {
        console.error('❌ Migration failed:', error.message);
        process.exit(1);
    } finally {
        if (connection) {
            await connection.end();
        }
    }
}

runMigration();

