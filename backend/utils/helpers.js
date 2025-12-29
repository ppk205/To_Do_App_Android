const crypto = require('crypto');

// Generate unique ID
function generateUserId() {
    return crypto.randomBytes(16).toString('hex');
}

// Remove sensitive data from user object
function sanitizeUser(user) {
    return {
        id: user.id,
        username: user.username,
        displayName: user.displayName,
        email: user.email,
        phone: user.phone,
        avatarUrl: user.avatarUrl,
        avatarId: user.avatarId,
        bio: user.bio,
        verified: user.verified,
        createdAt: user.createdAt,
        updatedAt: user.updatedAt
    };
}

module.exports = {
    generateUserId,
    sanitizeUser
};

