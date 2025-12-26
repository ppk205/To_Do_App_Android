const fs = require('fs');
const pool = require('./config/database');

async function runMigration() {
    console.log('🔄 Starting migration...\n');

    try {
        // Check current tables
        console.log('📋 Checking existing tables...');
        const [tables] = await pool.query('SHOW TABLES');
        console.log('Current tables:', tables.map(r => Object.values(r)[0]).join(', '));

        // Check if user_sessions already exists
        const tableExists = tables.some(t => Object.values(t)[0] === 'user_sessions');
        if (tableExists) {
            console.log('\n⚠️  user_sessions table already exists. Dropping it...');
            await pool.query('DROP TABLE IF EXISTS user_sessions');
            console.log('✅ Old table dropped');
        }

        // Read and run migration
        const sql = fs.readFileSync('./database/migrations/002_create_user_sessions.sql', 'utf8');
        console.log('\n🔄 Creating user_sessions table...');

        await pool.query(sql);
        console.log('✅ Migration successful: user_sessions table created\n');

        // Verify table structure
        const [structure] = await pool.query('DESCRIBE user_sessions');
        console.log('📊 user_sessions table structure:');
        console.table(structure.map(row => ({
            Field: row.Field,
            Type: row.Type,
            Null: row.Null,
            Key: row.Key,
            Default: row.Default
        })));

        // Verify foreign key
        const [fks] = await pool.query(`
            SELECT CONSTRAINT_NAME, COLUMN_NAME, REFERENCED_TABLE_NAME, REFERENCED_COLUMN_NAME
            FROM INFORMATION_SCHEMA.KEY_COLUMN_USAGE
            WHERE TABLE_SCHEMA = DATABASE()
            AND TABLE_NAME = 'user_sessions'
            AND REFERENCED_TABLE_NAME IS NOT NULL
        `);

        if (fks.length > 0) {
            console.log('\n🔗 Foreign keys:');
            console.table(fks);
        }

        console.log('\n✅ Migration completed successfully!');
        await pool.end();
        process.exit(0);

    } catch (err) {
        console.error('\n❌ Migration failed!');
        console.error('Error:', err.message);
        if (err.sqlMessage) {
            console.error('SQL Error:', err.sqlMessage);
        }
        if (err.sql) {
            console.error('SQL:', err.sql);
        }
        await pool.end();
        process.exit(1);
    }
}

runMigration();

