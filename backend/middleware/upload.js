const multer = require('multer');
const path = require('path');
const fs = require('fs');

// Create uploads directory if it doesn't exist
const uploadDir = path.join(__dirname, '../uploads/avatars');
if (!fs.existsSync(uploadDir)) {
    fs.mkdirSync(uploadDir, { recursive: true });
}

// Configure storage
const storage = multer.diskStorage({
    destination: function (req, file, cb) {
        cb(null, uploadDir);
    },
    filename: function (req, file, cb) {
        // Generate unique filename: userId-timestamp.ext
        const userId = req.user?.id || 'unknown';
        const ext = path.extname(file.originalname);
        const filename = `${userId}-${Date.now()}${ext}`;
        cb(null, filename);
    }
});

// File filter - only images
const fileFilter = (req, file, cb) => {
    // Debug: Log incoming file info
    console.log('📁 File upload attempt:', {
        fieldname: file.fieldname,
        originalname: file.originalname,
        mimetype: file.mimetype,
        encoding: file.encoding
    });

    const allowedTypes = /jpeg|jpg|png|gif|webp/;
    const extname = allowedTypes.test(path.extname(file.originalname).toLowerCase());
    const mimetype = allowedTypes.test(file.mimetype);

    // Accept if EITHER extension OR mimetype matches (more lenient)
    if (mimetype || extname) {
        console.log('✅ File accepted:', file.originalname);
        return cb(null, true);
    } else {
        console.log('❌ File rejected:', file.originalname, 'MIME:', file.mimetype);
        cb(new Error('Chỉ chấp nhận file ảnh (jpeg, jpg, png, gif, webp)'));
    }
};

// Configure multer
const upload = multer({
    storage: storage,
    limits: {
        fileSize: 5 * 1024 * 1024 // 5MB max
    },
    fileFilter: fileFilter
});

module.exports = upload;
