// Encryption Controller for E2EE key management
const db = require('../config/database');

/**
 * Initialize user encryption (first time setup)
 * POST /api/encryption/initialize
 */
exports.initializeUserEncryption = async (req, res) => {
    const { userId, salt } = req.body;

    if (!userId || !salt) {
        return res.status(400).json({
            success: false,
            message: 'userId and salt are required'
        });
    }

    try {
        // Check if already initialized
        const [existing] = await db.query(
            'SELECT user_id FROM user_encryption WHERE user_id = ?',
            [userId]
        );

        if (existing.length > 0) {
            return res.status(200).json({
                success: true,
                message: 'User encryption already initialized',
                userId,
                salt: existing[0].salt
            });
        }

        // Insert new salt
        await db.query(
            'INSERT INTO user_encryption (user_id, salt) VALUES (?, ?)',
            [userId, salt]
        );

        res.status(201).json({
            success: true,
            message: 'User encryption initialized',
            userId,
            salt
        });
    } catch (error) {
        console.error('Initialize encryption error:', error);
        res.status(500).json({
            success: false,
            message: 'Failed to initialize encryption'
        });
    }
};

/**
 * Get user's salt for password-based key derivation
 * GET /api/encryption/salt/:userId
 */
exports.getUserSalt = async (req, res) => {
    const { userId } = req.params;

    try {
        const [rows] = await db.query(
            'SELECT user_id, salt, created_at FROM user_encryption WHERE user_id = ?',
            [userId]
        );

        if (rows.length === 0) {
            return res.status(404).json({
                success: false,
                message: 'User encryption not initialized'
            });
        }

        res.json({
            success: true,
            userId: rows[0].user_id,
            salt: rows[0].salt,
            createdAt: rows[0].created_at
        });
    } catch (error) {
        console.error('Get salt error:', error);
        res.status(500).json({
            success: false,
            message: 'Failed to get salt'
        });
    }
};

/**
 * Set team encryption key
 * POST /api/encryption/team-key
 */
exports.setTeamKey = async (req, res) => {
    const { teamId, encryptedKey, passwordDerivedKey } = req.body;
    const userId = req.user.id; // From auth middleware

    if (!teamId || !encryptedKey) {
        return res.status(400).json({
            success: false,
            message: 'teamId and encryptedKey are required'
        });
    }

    try {
        // Verify user is manager or co-manager
        const [membership] = await db.query(
            'SELECT role FROM teammember WHERE teamId = ? AND userId = ?',
            [teamId, userId]
        );

        if (membership.length === 0 ||
            !['manager', 'co-manager'].includes(membership[0].role.toLowerCase())) {
            return res.status(403).json({
                success: false,
                message: 'Only managers can set team encryption keys'
            });
        }

        // Check if key already exists
        const [existing] = await db.query(
            'SELECT id, version FROM team_encryption_keys WHERE team_id = ?',
            [teamId]
        );

        if (existing.length > 0) {
            // Update existing key (increment version)
            const newVersion = existing[0].version + 1;
            await db.query(
                'UPDATE team_encryption_keys SET encrypted_key = ?, version = ?, updated_at = NOW() WHERE team_id = ?',
                [encryptedKey, newVersion, teamId]
            );
        } else {
            // Insert new key
            await db.query(
                'INSERT INTO team_encryption_keys (team_id, encrypted_key, created_by) VALUES (?, ?, ?)',
                [teamId, encryptedKey, userId]
            );
        }

        res.json({
            success: true,
            message: 'Team encryption key set successfully',
            teamId
        });
    } catch (error) {
        console.error('Set team key error:', error);
        res.status(500).json({
            success: false,
            message: 'Failed to set team key'
        });
    }
};

/**
 * Get team encryption key
 * POST /api/encryption/team-key/get
 */
exports.getTeamKey = async (req, res) => {
    const { teamId, passwordDerivedKey } = req.body;
    const userId = req.user.id; // From auth middleware

    if (!teamId) {
        return res.status(400).json({
            success: false,
            message: 'teamId is required'
        });
    }

    try {
        // Verify user is team member
        const [membership] = await db.query(
            'SELECT role FROM teammember WHERE teamId = ? AND userId = ?',
            [teamId, userId]
        );

        if (membership.length === 0) {
            return res.status(403).json({
                success: false,
                message: 'Not a member of this team'
            });
        }

        // Get encrypted team key
        const [keyRows] = await db.query(
            'SELECT encrypted_key, version FROM team_encryption_keys WHERE team_id = ?',
            [teamId]
        );

        if (keyRows.length === 0) {
            return res.status(404).json({
                success: false,
                message: 'Team encryption key not found'
            });
        }

        // Track access
        await db.query(
            `INSERT INTO team_key_access (team_id, user_id, access_count)
             VALUES (?, ?, 1)
             ON DUPLICATE KEY UPDATE access_count = access_count + 1, last_accessed = NOW()`,
            [teamId, userId]
        );

        res.json({
            success: true,
            teamId,
            encryptedKey: keyRows[0].encrypted_key,
            version: keyRows[0].version
        });
    } catch (error) {
        console.error('Get team key error:', error);
        res.status(500).json({
            success: false,
            message: 'Failed to get team key'
        });
    }
};

/**
 * Sync all team keys for user (for new device login)
 * POST /api/encryption/team-keys/sync
 */
exports.syncTeamKeys = async (req, res) => {
    const { passwordDerivedKey } = req.body;
    const userId = req.user.id; // From auth middleware

    try {
        // Get all teams user is member of
        const [teams] = await db.query(
            'SELECT teamId FROM teammember WHERE userId = ?',
            [userId]
        );

        if (teams.length === 0) {
            return res.json({
                success: true,
                message: 'No teams found',
                teamKeys: {}
            });
        }

        const teamIds = teams.map(t => t.teamId);

        // Get all encrypted keys for these teams
        const [keys] = await db.query(
            'SELECT team_id, encrypted_key FROM team_encryption_keys WHERE team_id IN (?)',
            [teamIds]
        );

        // Build response map
        const teamKeys = {};
        keys.forEach(key => {
            teamKeys[key.team_id] = key.encrypted_key;
        });

        res.json({
            success: true,
            message: `Synced ${keys.length} team keys`,
            teamKeys
        });
    } catch (error) {
        console.error('Sync team keys error:', error);
        res.status(500).json({
            success: false,
            message: 'Failed to sync team keys'
        });
    }
};

/**
 * Rotate team encryption key (manager only)
 * POST /api/encryption/team-key/rotate
 */
exports.rotateTeamKey = async (req, res) => {
    const { teamId, newEncryptedKey } = req.body;
    const userId = req.user.id;

    if (!teamId || !newEncryptedKey) {
        return res.status(400).json({
            success: false,
            message: 'teamId and newEncryptedKey are required'
        });
    }

    try {
        // Verify user is manager
        const [membership] = await db.query(
            'SELECT role FROM teammember WHERE teamId = ? AND userId = ?',
            [teamId, userId]
        );

        if (membership.length === 0 || membership[0].role.toLowerCase() !== 'manager') {
            return res.status(403).json({
                success: false,
                message: 'Only team manager can rotate encryption key'
            });
        }

        // Update key with new version
        const [result] = await db.query(
            'UPDATE team_encryption_keys SET encrypted_key = ?, version = version + 1, updated_at = NOW() WHERE team_id = ?',
            [newEncryptedKey, teamId]
        );

        if (result.affectedRows === 0) {
            return res.status(404).json({
                success: false,
                message: 'Team encryption key not found'
            });
        }

        res.json({
            success: true,
            message: 'Team encryption key rotated successfully',
            teamId
        });
    } catch (error) {
        console.error('Rotate team key error:', error);
        res.status(500).json({
            success: false,
            message: 'Failed to rotate team key'
        });
    }
};

