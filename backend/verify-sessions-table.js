const pool = require('./config/database');

async function verify() {
    try {
        console.log('🔍 Verifying user_sessions table...\n');

        const [structure] = await pool.query('DESCRIBE user_sessions');
        console.log('✅ Table exists! Structure:');
        console.table(structure);

        await pool.end();
        process.exit(0);
    } catch (err) {
        console.error('❌ Error:', err.message);
        await pool.end();
        process.exit(1);
    }
}

verify();

