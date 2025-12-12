const crypto = require('crypto');

// Generate unique ID
function generateUserId() {
    return crypto.randomBytes(16).toString('hex');
}

// Remove sensitive data from user object
function sanitizeUser(user) {
    const { hashedPassword, ...sanitizedUser } = user;
    return sanitizedUser;
}

module.exports = {
    generateUserId,
    sanitizeUser
};

