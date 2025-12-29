const express = require('express');
const cors = require('cors');
require('dotenv').config();

const authRoutes = require('./routes/authRoutes');
const { redisClient, connectRedis, quit: quitRedis } = require('./config/redis'); // Import Redis client and helpers

const app = express();
const DEFAULT_PORT = parseInt(process.env.PORT, 10) || 3001;

// Middleware
app.use(cors());
app.use(express.json());
app.use(express.urlencoded({ extended: true }));

// Routes
app.use('/api/auth', authRoutes);

// Root route
app.get('/', (req, res) => {
    res.json({
        message: 'MOPR Backend API Server',
        version: '1.0.0',
        endpoints: {
            register: 'POST /api/auth/register',
            login: 'POST /api/auth/login'
        }
    });
});

// Error handling middleware
app.use((err, req, res, next) => {
    console.error(err.stack);
    res.status(500).json({
        success: false,
        message: 'Đã xảy ra lỗi server',
        error: process.env.NODE_ENV === 'development' ? err.message : undefined
    });
});

// Start server with robust EADDRINUSE handling and fallback
async function startServer(port = DEFAULT_PORT, maxRetries = 3) {
    // Try to connect to Redis (non-fatal if it fails)
    try {
        await connectRedis();
    } catch (err) {
        console.error('Redis initialization failed (non-fatal):', err.message);
    }

    const server = app.listen(port, () => {
        console.log(`🚀 Server is running on port ${port}`);
        console.log(`📍 API URL: http://localhost:${port}`);
        console.log(`🌍 Environment: ${process.env.NODE_ENV}`);
    });

    server.on('error', (err) => {
        if (err && err.code === 'EADDRINUSE') {
            console.error(`Port ${port} is already in use.`);
            if (maxRetries > 0) {
                const nextPort = port + 1;
                console.log(`Trying to start on port ${nextPort} (retries left: ${maxRetries - 1})...`);
                setTimeout(() => startServer(nextPort, maxRetries - 1), 500);
            } else {
                console.error(`Failed to start server after trying multiple ports.`);
                console.error(`Possible fixes:
- Stop the process currently using port ${port} (Windows: 'netstat -ano | findstr ${port}' then 'taskkill /PID <pid> /F')
- Or set a different PORT in your environment: 'PORT=4000 npm run dev' (or on Windows PowerShell: '$env:PORT=4000; npm run dev')
`);
                process.exit(1);
            }
        } else {
            console.error('Server error:', err);
            process.exit(1);
        }
    });
}

startServer();

// Graceful shutdown handling
async function gracefulShutdown(signal) {
    console.log(`${signal} received, closing Redis connection...`);
    await quitRedis();
    console.log('Redis connection closed.');
    process.exit(0);
}

process.on('SIGTERM', () => gracefulShutdown('SIGTERM'));
process.on('SIGINT', () => gracefulShutdown('SIGINT'));
