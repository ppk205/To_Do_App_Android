const redis = require('redis');

// Hỗ trợ cả REDIS_URL (rediss:// hoặc redis://) và biến riêng lẻ
const { createClient } = redis;
const REDIS_URL = process.env.REDIS_URL;

const clientOptions = {};

if (REDIS_URL) {
  clientOptions.url = REDIS_URL;
  // Nếu URL là rediss://, node-redis sẽ bật TLS tự động.
  try {
    const parsed = new URL(REDIS_URL);
    if (parsed.protocol === 'rediss:') {
      clientOptions.socket = clientOptions.socket || {};
      clientOptions.socket.rejectUnauthorized = process.env.REDIS_TLS_REJECT_UNAUTHORIZED !== 'false';
    }
  } catch (e) {
    // ignore
  }
} else {
  // Fallback dùng biến môi trường rời
  clientOptions.socket = {
    host: process.env.REDIS_HOST || '127.0.0.1',
    port: process.env.REDIS_PORT ? parseInt(process.env.REDIS_PORT, 10) : 6379,
  };

  if (process.env.REDIS_USERNAME) clientOptions.username = process.env.REDIS_USERNAME;
  if (process.env.REDIS_PASSWORD) clientOptions.password = process.env.REDIS_PASSWORD;
}

const redisClient = createClient(clientOptions);

// Logging events
redisClient.on('error', (err) => {
  console.error('Redis Client Error:', err);
});
redisClient.on('connect', () => console.log('Redis connecting...'));
redisClient.on('ready', () => console.log('✅ Redis connected and ready'));
redisClient.on('reconnecting', () => console.log('Redis reconnecting...'));

// Connect immediately when module is required
(async () => {
  try {
    await redisClient.connect();
  } catch (err) {
    console.error('❌ Redis connection error:', err);
  }
})();

// Exports
module.exports = redisClient;

// Helper: chờ ready
module.exports.waitForReady = async function waitForReady(timeout = 5000) {
  if (redisClient.isReady) return redisClient;
  return new Promise((resolve, reject) => {
    const timer = setTimeout(() => reject(new Error('Redis ready timeout')), timeout);
    const onReady = () => {
      clearTimeout(timer);
      resolve(redisClient);
    };
    redisClient.once('ready', onReady);
  });
};

module.exports.quit = async function quitRedis() {
  try {
    if (redisClient.isOpen) await redisClient.quit();
  } catch (e) {
    // ignore
  }
};
