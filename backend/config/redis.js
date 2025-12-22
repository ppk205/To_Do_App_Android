const redis = require('redis');

// Tạo Redis client
const redisClient = redis.createClient({
    host: process.env.REDIS_HOST || 'localhost',
    port: process.env.REDIS_PORT || 6379,
    password: process.env.REDIS_PASSWORD || undefined,
    // Với Redis 4.x, sử dụng cấu hình mới:
    socket: {
        host: process.env.REDIS_HOST || 'localhost',
        port: process.env.REDIS_PORT || 6379
    },
    password: process.env.REDIS_PASSWORD || undefined
});

// Kết nối Redis
redisClient.connect()
    .then(() => {
        console.log('✅ Redis connected successfully');
    })
    .catch((err) => {
        console.error('❌ Redis connection error:', err);
    });

// Handle errors
redisClient.on('error', (err) => {
    console.error('Redis Client Error:', err);
});

module.exports = redisClient;

