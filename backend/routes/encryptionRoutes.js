// Encryption routes for E2EE key management
const express = require('express');
const router = express.Router();
const encryptionController = require('../controllers/encryptionController');
const { authenticateToken } = require('../middleware/authMiddleware');

/**
 * Initialize user encryption (first time setup)
 * POST /api/encryption/initialize
 * Body: { userId, salt }
 */
router.post('/initialize', authenticateToken, encryptionController.initializeUserEncryption);

/**
 * Get user's salt for password-based key derivation
 * GET /api/encryption/salt/:userId
 */
router.get('/salt/:userId', authenticateToken, encryptionController.getUserSalt);

/**
 * Set team encryption key (manager/co-manager only)
 * POST /api/encryption/team-key
 * Body: { teamId, encryptedKey, passwordDerivedKey }
 */
router.post('/team-key', authenticateToken, encryptionController.setTeamKey);

/**
 * Get team encryption key
 * POST /api/encryption/team-key/get
 * Body: { teamId, passwordDerivedKey }
 */
router.post('/team-key/get', authenticateToken, encryptionController.getTeamKey);

/**
 * Sync all team keys for user (for new device login)
 * POST /api/encryption/team-keys/sync
 * Body: { passwordDerivedKey }
 */
router.post('/team-keys/sync', authenticateToken, encryptionController.syncTeamKeys);

/**
 * Rotate team encryption key (manager only)
 * POST /api/encryption/team-key/rotate
 * Body: { teamId, newEncryptedKey }
 */
router.post('/team-key/rotate', authenticateToken, encryptionController.rotateTeamKey);

module.exports = router;

